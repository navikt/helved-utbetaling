package fakes

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import libs.auth.*
import libs.ktor.port
import java.net.URI

object Azp {
    const val AAP = "test:aap:utbetal"
    const val DAGPENGER = "test:helved:snickerboa"
    const val TILTAKSPENGER = "test:helved:tiltakspenger-saksbehandling-api"
    const val TILLEGGSSTØNADER = "test:helved:tilleggsstonader-sak"
    const val AZURE_TOKEN_GENERATOR = "test:helved:azure-token-generator"
}

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

    fun generateToken(
        azp_name: String = Azp.TILLEGGSSTØNADER,
        tokenResponseStatus: Int? = null,
        navIdent: String? = null,
    ) =
        jwksGenerator.generate(
            buildList {
                add(Claim("azp_name", azp_name))
                tokenResponseStatus?.let { add(Claim("token_response_status", it.toString())) }
                navIdent?.let { add(Claim("NAVident", it)) }
            },
        )

    override fun close() = azure.stop(0, 0)
}

private fun Application.azure() {
    install(ContentNegotiation) {
        json(libs.kotlinx.KotlinxJson)
    }

    routing {
        get("/jwks") {
            call.respondText(TEST_JWKS)
        }

        post("/token") {
            val status = call.receiveText().tokenResponseStatus()
            if (status == null) {
                call.respond(AzureToken(3600, "token"))
            } else {
                call.respond(HttpStatusCode.fromValue(status))
            }
        }
    }
}

private fun String.tokenResponseStatus(): Int? {
    val assertion = substringAfter("assertion=", "").substringBefore("&")
    if (assertion.isEmpty()) return null
    val payload = assertion.substringAfter('.').substringBefore('.')
    val claims = java.util.Base64.getUrlDecoder().decode(payload).decodeToString()
    return "\"token_response_status\":\"(\\d+)\"".toRegex().find(claims)?.groupValues?.get(1)?.toInt()
}
