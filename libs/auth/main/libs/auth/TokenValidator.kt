package libs.auth

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import libs.utils.secureLog
import java.net.http.HttpClient
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Usage:
 * ```
 *  install(Authentication) {
 *      jwt(TokenProvider.AZURE, AzureConfig()) { jwt ->
 *          jwt.getClaim("pid", String::class) != null
 *      }
 *  }
 *  ```
 */
fun AuthenticationConfig.jwt(
    name: String,
    config: TokenConfig,
    customValidation: (Jwt.Claims) -> Boolean = { true },
) {
    val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(1))
        .build()
    val jwks = JwksClient(config.jwks, http)
    val verifier = JwtVerifier(jwks, config)
    bearer(name) {
        realm = name
        authenticate { credential ->
            try {
                val jwt = verifier.verify(credential.token)
                if (!customValidation(jwt.claims)) {
                    secureLog.warn("Custom validation failed")
                    return@authenticate null
                }
                JwtPrincipal(jwt.claims)
            } catch (e: Exception) {
                secureLog.warn("Token validation failed: ${e.message}")
                null // auto 401
            }
        }
    }
}

data class JwtPrincipal(val claims: Jwt.Claims)

