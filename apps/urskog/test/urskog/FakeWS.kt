package urskog

import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import io.ktor.http.*
import io.ktor.serialization.*
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import libs.auth.AzureConfig
import libs.auth.AzureToken
import libs.auth.TEST_JWKS
import libs.http.HttpClientFactory
import libs.ktor.port
import libs.utils.Resource
import libs.ws.*
import java.net.URI
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.util.*

class FakeWS: Sts, Soap {
    var respondWith: String = Resource.read("/simuler-ok.xml")
    val received = mutableListOf<String>()

    override val http: HttpClient = HttpClientFactory.new(models.kotlinx.KotlinxJson)

    override suspend fun samlToken(): SamlToken {
        return SamlToken("token", LocalDateTime.now())
    }

    override suspend fun call(action: String, body: String): String {
        return SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = "http://localhost:8083".let(::URI).toURL(),
            assertion = "token",
            body = respondWith
        )
    }
}

class HttpFakes: AutoCloseable {
    private val ktor = embeddedServer(Netty, port = 0, module = Application::fakes).apply { start() }
    override fun close() = ktor.stop()

    val proxyConfig: ProxyConfig by lazy {
        ProxyConfig(
            host = "http://localhost:${ktor.engine.port}".let(::URI).toURL(),
            scope = "test",
        )
    }
    val azureConfig: AzureConfig by lazy {
        AzureConfig(
            tokenEndpoint = "http://localhost:${ktor.engine.port}/token".let(::URI).toURL(),
            jwks = "http://localhost:${ktor.engine.port}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "",
            clientSecret = ""
        )
    }
    val simuleringConfig: SoapConfig by lazy {
        SoapConfig(
            host = URI("http://localhost:${ktor.engine.port}/cics").toURL(),
            sts = StsConfig(
                host = "http://localhost:${ktor.engine.port}/gandalf".let(::URI).toURL(),
                user = "",
                pass = "",
            )
        )
    }
}

private fun Application.fakes() {
    class XmlDeserializer : ContentConverter {
        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel) = null
        override suspend fun serialize(contentType: ContentType, charset: Charset, typeInfo: TypeInfo, value: Any?) = null
    }


    install(ContentNegotiation) {
        json(models.kotlinx.KotlinxJson)
        register(ContentType.Application.Xml, XmlDeserializer())
    }

    routing {
        get("/gandalf/rest/v1/sts/samltoken") {
            call.respond(GandalfOIDCSamlToken())
        }
        post("/cics") {
            TestRuntime.ws.received.add(call.receive())
            call.respondText(TestRuntime.ws.call("test", "test"))
        }
        get("/jwks") {
            call.respondText(TEST_JWKS)
        }

        post("/token") {
            call.respond(AzureToken(3600, "token"))
        }
    }
}

@Serializable
private data class GandalfOIDCSamlToken(
    val access_token: String = "aGVtbWVsaWcuZ2FuZGFsZi50b2tlbgo=",
    val issued_token_type: String = "urn:ietf:params:oauth:token-type:saml2",
    val token_type: String = "Bearer",
    val expires_in: Long = 3600,
)
