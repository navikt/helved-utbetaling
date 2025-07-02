package libs.auth

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import libs.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.coroutines.runBlocking
import io.ktor.server.auth.*
import java.net.URI

fun Application.module(config: AzureConfig) {

    install(ContentNegotiation) {
        jackson {}
    }

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config)
        }
    }

    routing {
        route("/open") {
            get { 
                call.respond(HttpStatusCode.OK)
            }
        }

        authenticate(TokenProvider.AZURE) {
            route("/secure") {
                get {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}

class AzureFake: AutoCloseable {
    companion object {
        fun azure(app: Application) {
            app.install(ContentNegotiation) { jackson() }
            app.routing {
                get("/jwks") {
                    call.respondText(libs.auth.TEST_JWKS)
                }

                post("/token") {
                    call.respond(libs.auth.AzureToken(3600, "token"))
                }
            }
        }
    }
    private val server = embeddedServer(Netty, port = 0) { AzureFake.azure(this) }.apply { start() }

    val config by lazy {
        AzureConfig(
            tokenEndpoint = "http://localhost:${server.engine.port}/token".let(::URI).toURL(),
            jwks = "http://localhost:${server.engine.port}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "hei",
            clientSecret = "på deg"
        )
    }

    private val jwksGenerator = libs.auth.JwkGenerator(config.issuer, config.clientId)

    fun generateToken() = jwksGenerator.generate()

    override fun close() = server.stop(0, 0)
}

private val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

