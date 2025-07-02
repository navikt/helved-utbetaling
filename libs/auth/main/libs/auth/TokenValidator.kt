package libs.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import libs.utils.secureLog
import java.util.*
import java.util.concurrent.TimeUnit

fun JWTAuthenticationProvider.Config.configure(
    config: TokenConfig,
    customValidation: (JWTCredential) -> Boolean = { true },
) {
    val validator = TokenValidator(config, this)
    validator.configure(customValidation)
}

/**
 * Usage:
 * ```
 *  install(Authentication) {
 *      jwt(TokenProvider.AZURE) {
 *          configure(AzureConfig()) { jwt ->
 *              jwt.getClaim("pid", String::class) != null
 *          }
 *      }
 *  }
 *  ```
 */
class TokenValidator(
    private val config: TokenConfig,
    private val auth: JWTAuthenticationProvider.Config,
) {
    private val jwkProvider: JwkProvider = JwkProviderBuilder(config.jwks)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    internal fun configure(customValidation: (JWTCredential) -> Boolean = { true }) {
        auth.apply {
            verifier(jwkProvider, config.issuer)
            challenge { _, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Ugyldig token for realm $realm")
            }
            validate { cred ->
                val now = Date()

                if (config.clientId !in cred.audience) {
                    secureLog.warn("Validering av token feilet (clientId var ikke i audience: ${cred.audience}")
                    return@validate null
                }

                if (cred.expiresAt?.before(now) == true) {
                    secureLog.warn("Validering av token feilet (expired at: ${cred.expiresAt})")
                    return@validate null
                }

                if (cred.notBefore?.after(now) == true) {
                    secureLog.warn("Validering av token feilet (not valid yet, valid from: ${cred.notBefore})")
                    return@validate null
                }

                if (cred.issuedAt?.after(cred.expiresAt ?: return@validate null) == true) {
                    secureLog.warn("Validering av token feilet (issued after expiration: ${cred.issuedAt} )")
                    return@validate null
                }

                if (!customValidation(cred)) {
                    secureLog.warn("Validering av token feilet (Custom validering feilet)")
                    return@validate null
                }

                JWTPrincipal(cred.payload)
            }
        }
    }
}