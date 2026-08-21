package urskog

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import libs.auth.AzureConfig
import libs.auth.AzureToken
import libs.auth.TEST_JWKS
import libs.ktor.port
import java.net.URI
import java.nio.charset.Charset

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
}

private fun Application.fakes() {
    class XmlDeserializer : ContentConverter {
        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel) = null
        override suspend fun serialize(contentType: ContentType, charset: Charset, typeInfo: TypeInfo, value: Any?) = null
    }

    install(ContentNegotiation) {
        json(libs.kotlinx.KotlinxJson)
        register(ContentType.Application.Xml, XmlDeserializer())
    }

    routing {
        get("/jwks") {
            call.respondText(TEST_JWKS)
        }

        post("/token") {
            call.respond(AzureToken(3600, "token"))
        }
    }
}

