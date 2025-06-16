package peisschtappern

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.auth.*
import libs.ktor.*
import java.net.URI

class AzureFake : AutoCloseable {
    private val azure = embeddedServer(Netty, port = 0, module = Application::azure).apply { start() }

    val config by lazy {
        AzureConfig(
            tokenEndpoint = "http://localhost:${azure.engine.port}/token".let(::URI).toURL(),
            jwks = "http://localhost:${azure.engine.port}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "hei",
            clientSecret = "på deg"
        )
    }

    private val jwksGenerator = JwkGenerator(config.issuer, config.clientId)

    fun generateToken() = jwksGenerator.generate()

    override fun close() = azure.stop(0, 0)
}

private fun Application.azure() {
    install(ContentNegotiation) {
        jackson {}
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

