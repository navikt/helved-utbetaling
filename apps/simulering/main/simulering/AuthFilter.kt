@file:Suppress("DEPRECATION")

package simulering

import libs.auth.Jwt
import libs.auth.JwksClient
import libs.auth.JwtVerifier
import libs.auth.TokenConfig
import libs.utils.secureLog
import org.http4k.core.*
import org.http4k.lens.RequestContextLens
import java.net.http.HttpClient
import java.time.Duration

fun azureAuthFilter(verifier: JwtVerifier, claimsLens: RequestContextLens<Jwt.Claims>): Filter = Filter { next ->
    { request ->
        val token = request.header("Authorization")?.removePrefix("Bearer ")
        if (token == null) {
            Response(Status.UNAUTHORIZED).body("Missing Authorization header")
        } else {
            val jwt = try {
                verifier.verify(token)
            } catch (e: Exception) {
                secureLog.warn("Token validation failed: ${e.message}")
                null
            }
            if (jwt == null) {
                Response(Status.UNAUTHORIZED).body("Invalid token")
            } else {
                next(request.with(claimsLens of jwt.claims))
            }
        }
    }
}

fun createJwtVerifier(config: AzureConfig): JwtVerifier {
    val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(1))
        .build()
    val jwks = JwksClient(config.jwks, http)
    val tokenConfig = TokenConfig(
        clientId = config.clientId,
        jwks = config.jwks,
        issuer = config.issuer,
    )
    return JwtVerifier(jwks, tokenConfig)
}

fun Jwt.Claims.clientName(): String =
    (claim("azp_name") ?: error("Missing azp_name claim"))
        .substringAfterLast(":")
