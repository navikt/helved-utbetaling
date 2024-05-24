package simulering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.runBlocking
import libs.auth.AzureConfig
import libs.auth.JwkGenerator
import libs.auth.TEST_JWKS
import libs.auth.Token
import libs.utils.Resource
import libs.ws.*
import java.net.URI
import java.time.LocalDateTime
import java.util.*

class TestRuntime : Sts, Soap, AutoCloseable {
    private val ktor = embeddedServer(Netty, 0) {
        fakes(this@TestRuntime)
    }.apply { start() }

    val config: Config
        get() = Config(
            proxy = ProxyConfig(
                host = "http://localhost:${ktor.port}".let(::URI).toURL(),
                scope = "test"
            ),
            azure = AzureConfig(
                tokenEndpoint = "http://localhost:${ktor.port}/token".let(::URI).toURL(),
                jwks = "http://localhost:${ktor.port}/jwks".let(::URI).toURL(),
                issuer = "test",
                clientId = "",
                clientSecret = ""
            ),
            simulering = SoapConfig(
                host = "http://localhost:${ktor.port}/cics".let(::URI).toURL(),
                sts = StsConfig(
                    host = "http://localhost:${ktor.port}/gandalf".let(::URI).toURL(),
                    user = "",
                    pass = "",
                )
            )
        )

    override fun close() = ktor.stop(0L, 0L)

    override suspend fun samlToken(): SamlToken {
        return SamlToken("token", LocalDateTime.now())
    }

    val receivedSoapRequests: MutableList<String> = mutableListOf()

    internal fun registerSoapRequest(req: String) {
        receivedSoapRequests.add(req)
    }

    var soapResponse: String = Resource.read("/simuler-body-response.xml")

    fun soapRespondWith(resource: String) {
    }

    override suspend fun call(action: String, body: String): String {
        return SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = "http://localhost:8083".let(::URI).toURL(),
            assertion = "token",
            body = soapResponse
        )
    }

    private val jwksGenerator = JwkGenerator(config.azure.issuer, config.azure.clientId)

    fun generateToken() = jwksGenerator.generate()
}

private fun Application.fakes(fake: TestRuntime) {
    class XmlDeserializer : ContentConverter {
        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            return null
        }
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        register(ContentType.Application.Xml, XmlDeserializer())
    }

    data class GandalfOIDCSamlToken(
        val access_token: String = "aGVtbWVsaWcuZ2FuZGFsZi50b2tlbgo=",
        val issued_token_type: String = "urn:ietf:params:oauth:token-type:saml2",
        val token_type: String = "Bearer",
        val expires_in: Long = 3600,
    )

    routing {
        get("/gandalf/rest/v1/sts/samltoken") {
            call.respond(GandalfOIDCSamlToken())
        }
        post("/cics") {
            fake.registerSoapRequest(call.receive())
            call.respondText(fake.call("test", "test"))
        }
        get("/jwks") {
            call.respondText(TEST_JWKS)
        }

        post("/token") {
            call.respond(Token(3600, "token"))
        }
    }
}

private val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }