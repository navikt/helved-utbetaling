package abetal

import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.auth.AzureConfig
import libs.auth.JwkGenerator
import libs.auth.TEST_JWKS
import libs.ktor.KtorRuntime
import java.net.URI

class AzureFake {
    private val server = KtorRuntime<Nothing>(
        appName = "abetal.azure",
        jsonConfig = libs.kotlinx.KotlinxJson,
        module = {
            routing {
                get("/jwks") {
                    call.respondText(TEST_JWKS)
                }
            }
        },
    )

    val config = AzureConfig(
        tokenEndpoint = URI("http://localhost:${server.port}/token").toURL(),
        jwks = URI("http://localhost:${server.port}/jwks").toURL(),
        issuer = "test",
        clientId = "hei",
        clientSecret = "på deg",
    )

    private val jwksGenerator = JwkGenerator(config.issuer, config.clientId)

    fun generateToken() = jwksGenerator.generate()
}
