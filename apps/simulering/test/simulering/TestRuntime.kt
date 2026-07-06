package simulering

import libs.auth.JwkGenerator
import libs.auth.TEST_JWKS
import libs.kafka.KafkaProducerFake
import libs.kafka.StreamsMock
import libs.utils.Resource
import models.Simulering
import org.http4k.core.*
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.net.URI
import java.time.LocalDateTime
import java.util.*

class TestRuntime : Sts, Soap, AutoCloseable {
    private val server = fakeRoutes().asServer(SunHttp(0)).start()
    val kafka: StreamsMock = StreamsMock()
    private val port get() = server.port()

    val dryrunAap: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunAap)
    val dryrunDp: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunDp)
    val dryrunTs: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunTs)
    val dryrunTp: KafkaProducerFake<String, Simulering> get() = kafka.getProducer(Topics.dryrunTp)

    val config: Config
        get() = Config(
            proxy = ProxyConfig(
                host = "http://localhost:$port".let(::URI).toURL(),
                scope = "test",
                simuleringPath = "cics/oppdrag/simulerFpServiceWSBinding"
            ),
            azure = AzureConfig(
                tokenEndpoint = "http://localhost:$port/token".let(::URI).toURL(),
                jwks = "http://localhost:$port/jwks".let(::URI).toURL(),
                issuer = "test",
                clientId = "",
                clientSecret = ""
            ),
            simulering = SoapConfig(
                host = "http://localhost:$port/cics".let(::URI).toURL(),
                sts = StsConfig(
                    host = "http://localhost:$port/gandalf".let(::URI).toURL(),
                    user = "",
                    pass = "",
                )
            ),
            kafka = kafka.config.copy(additionalProperties = Properties().apply {
                put("state.dir", "build/kafka-streams")
                put("max.task.idle.ms", -1L)
                put(org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG, org.apache.kafka.streams.state.BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
            })
        )

    override fun close() {
        server.stop()
        libs.kafka.Names.clear()
    }

    override fun samlToken() = SamlToken("token", LocalDateTime.now())
    override fun invalidate() {}

    val receivedSoapRequests: MutableList<String> = mutableListOf()
    var stsCallCount = 0

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

    override fun call(action: String, body: String): String {
        val response = if (soapResponseQueue.size > 1) soapResponseQueue.removeFirst() else soapResponseQueue.first()
        return SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = "http://localhost:8083".let(::URI).toURL(),
            assertion = "token",
            body = response
        )
    }

    private val jwksGenerator by lazy { JwkGenerator(config.azure.issuer, config.azure.clientId) }
    fun generateToken() = jwksGenerator.generate()

    private fun fakeRoutes(): HttpHandler = routes(
        "/gandalf/rest/v1/sts/samltoken" bind Method.GET to {
            stsCallCount++
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"access_token":"aGVtbWVsaWcuZ2FuZGFsZi50b2tlbgo=","issued_token_type":"urn:ietf:params:oauth:token-type:saml2","token_type":"Bearer","expires_in":3600}""")
        },
        "/cics" bind Method.POST to { req ->
            receivedSoapRequests.add(req.bodyString())
            val responseBody = call("test", "test")
            Response(Status.OK)
                .header("Content-Type", "text/xml")
                .body(responseBody)
        },
        "/jwks" bind Method.GET to {
            Response(Status.OK).body(TEST_JWKS)
        },
        "/token" bind Method.POST to {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"expires_in":3600,"access_token":"token"}""")
        },
    )
}
