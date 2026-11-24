package simulering

import com.sun.net.httpserver.HttpServer
import libs.auth.*
import libs.kafka.KafkaProducerFake
import libs.kafka.StreamsMock
import libs.utils.Resource
import models.Simulering
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.util.*

object TestRuntime : Sts, Soap {
    private val kafkaMock: StreamsMock by lazy { StreamsMock() }
    val kafka: StreamsMock
        get() {
            app
            return kafkaMock
        }

    private val jwksServer: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/jwks") { exchange ->
                val body = TEST_JWKS.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }

    private val jwtVerifier: JwtVerifier by lazy {
        val jwksUrl = URI("http://localhost:${jwksServer.address.port}/jwks").toURL()
        val jwksClient = JwksClient(jwksUrl, HttpClient.newHttpClient())
        val tokenConfig = TokenConfig(clientId = "test-client", jwks = jwksUrl, issuer = "test")
        JwtVerifier(jwksClient, tokenConfig)
    }

    val app: HttpHandler by lazy {
        libs.kafka.Names.clear()
        app(
            config = config,
            kafka = kafkaMock,
            soap = this,
            sts = this,
            jwtVerifier = jwtVerifier,
            shadowHttp = { request ->
                receivedRestRequests.add(request)
                restHandler(request)
            },
            shadowAuthorization = { "Bearer shadow-test-token" },
        )
    }

    val config by lazy {
        Config(
            // proxy = ProxyConfig(
            //     host = URI("http://unused").toURL(),
            //     scope = "unused",
            //     simuleringPath = "unused",
            // ),
            azure = AzureConfig(
                tokenEndpoint = URI("http://unused").toURL(),
                jwks = URI("http://unused").toURL(),
                issuer = "test",
                clientId = "test-client",
                clientSecret = "",
            ),
            simulering = SimuleringConfig(
                proxy = ProxyConfig(
                    host = URI("http://unused").toURL(),
                    scope = "unused",
                    simuleringPath = "unused",
                ),
                soap = SoapConfig(
                    host = URI("http://unused").toURL(),
                    sts = StsConfig(
                        host = URI("http://unused").toURL(),
                        user = "",
                        pass = "",
                    ),
                ),
                sokos = SokosOppdragConfig(
                    cluster = "test-cluster",
                    host = URI("http://sokos.test").toURL(),
                    scope = "test-sokos",
                ),
            ),
            kafka = kafkaMock.config.copy(applicationId = "test-simulering-${UUID.randomUUID()}", additionalProperties = Properties().apply {
                val workerId = System.getProperty("org.gradle.test.worker") ?: "0"
                put("state.dir", "build/kafka-streams/state-w$workerId")
                put("max.task.idle.ms", -1L)
                put(
                    org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG,
                    org.apache.kafka.streams.state.BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java
                )
            }),
        )
    }

    val dryrunAap: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunAap)
    val dryrunDp: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunDp)
    val dryrunTs: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunTs)
    val dryrunTp: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunTp)
    val dryrunValp: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunValp)
    val dryrunHistorisk: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunHistorisk)

    // Sts fake
    override fun samlToken() = SamlToken("token", LocalDateTime.now())
    override fun invalidate() { invalidateCount++ }

    // Soap fake
    val receivedSoapRequests: MutableList<String> = mutableListOf()
    val receivedRestRequests = CopyOnWriteArrayList<Request>()
    var restHandler: HttpHandler = { emptyRestResponse() }
    val secureEvents = CopyOnWriteArrayList<ILoggingEvent>()
    private val secureCapture by lazy {
        object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) { secureEvents.add(event) }
        }.apply {
            start()
            (LoggerFactory.getLogger("secureLog") as Logger).addAppender(this)
        }
    }
    var invalidateCount = 0

    private val soapResponseQueue: ArrayDeque<String> = ArrayDeque<String>().apply {
        add(Resource.read("/simuler-body-response.xml"))
    }

    fun soapRespondWith(resource: String) {
        soapResponseQueue.clear()
        soapResponseQueue.add(resource)
    }

    fun soapRespondWithSequence(vararg responses: String) {
        soapResponseQueue.clear()
        soapResponseQueue.addAll(responses.toList())
    }

    fun reset() {
        secureCapture
        secureEvents.clear()
        receivedRestRequests.clear()
        restHandler = { emptyRestResponse() }
        kafkaMock.reset()
        soapResponseQueue.clear()
        soapResponseQueue.add(Resource.read("/simuler-body-response.xml"))
        receivedSoapRequests.clear()
        invalidateCount = 0
    }

    private fun emptyRestResponse() = Response(Status.OK).body("""
        {"OsSimulerBeregningOperationResponse":{"ZS1OCont":{"responsSimulering":{"statusKode":0}}}}
    """.trimIndent())

    fun shadowEvents(key: String): List<JsonObject> = secureEvents.mapNotNull {
        runCatching { Json.parseToJsonElement(it.formattedMessage).jsonObject }.getOrNull()
    }.filter { it["key"]?.jsonPrimitive?.content == key }

    fun awaitShadow(key: String) = runBlocking {
        withTimeout(5.seconds) {
            while (shadowEvents(key).none {
                it["event"]?.jsonPrimitive?.content in listOf("simulering_shadow_comparison", "simulering_shadow_failed", "simulering_shadow_skipped")
            }) delay(10.milliseconds)
        }
    }

    override fun call(action: String, body: String): String {
        receivedSoapRequests.add(body)
        val response = if (soapResponseQueue.size > 1) soapResponseQueue.removeFirst() else soapResponseQueue.first()
        return SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = URI("http://unused").toURL(),
            assertion = "token",
            body = response,
        )
    }

    private val jwkGenerator by lazy { JwkGenerator("test", "test-client") }
    fun generateToken(azpName: String? = null): String {
        val claims = listOfNotNull(azpName?.let { Claim("azp_name", it) })
        return jwkGenerator.generate(claims)
    }
}
