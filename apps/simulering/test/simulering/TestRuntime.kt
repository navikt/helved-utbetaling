package simulering

import com.sun.net.httpserver.HttpServer
import libs.auth.*
import libs.kafka.KafkaProducerFake
import libs.kafka.StreamsMock
import libs.utils.Resource
import models.Simulering
import org.http4k.core.HttpHandler
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.util.*

object TestRuntime : Sts, Soap {
    val kafka: StreamsMock by lazy { StreamsMock() }

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
        app(
            config = config,
            kafka = kafka,
            soap = this,
            sts = this,
            jwtVerifier = jwtVerifier,
        )
    }

    val config by lazy {
        Config(
            proxy = ProxyConfig(
                host = URI("http://unused").toURL(),
                scope = "unused",
                simuleringPath = "unused",
            ),
            azure = AzureConfig(
                tokenEndpoint = URI("http://unused").toURL(),
                jwks = URI("http://unused").toURL(),
                issuer = "test",
                clientId = "test-client",
                clientSecret = "",
            ),
            simulering = SoapConfig(
                host = URI("http://unused").toURL(),
                sts = StsConfig(
                    host = URI("http://unused").toURL(),
                    user = "",
                    pass = "",
                ),
            ),
            kafka = kafka.config.copy(additionalProperties = Properties().apply {
                put("state.dir", "build/kafka-streams")
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

    // Sts fake
    override fun samlToken() = SamlToken("token", LocalDateTime.now())
    override fun invalidate() { invalidateCount++ }

    // Soap fake
    val receivedSoapRequests: MutableList<String> = mutableListOf()
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
        soapResponseQueue.clear()
        soapResponseQueue.add(Resource.read("/simuler-body-response.xml"))
        receivedSoapRequests.clear()
        invalidateCount = 0
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
