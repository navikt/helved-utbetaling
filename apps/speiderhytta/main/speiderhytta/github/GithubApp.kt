package speiderhytta.github

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libs.utils.appLog
import speiderhytta.GithubConfig
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * GitHub App authentication.
 *
 * Flow:
 *  1. Sign a short-lived (10 min) RS256 JWT with the App's private key.
 *  2. Exchange JWT for a 1h installation access token.
 *  3. Cache token, refresh ~1 minute before expiry.
 */
class GithubApp(
    private val config: GithubConfig,
    private val client: HttpClient,
) {
    private val mutex = Mutex()
    private var cached: InstallationToken? = null
    private val privateKey: RSAPrivateKey by lazy { parsePrivateKey(config.privateKeyPem) }

    suspend fun token(): String = mutex.withLock {
        val now = Instant.now()
        val current = cached
        if (current != null && current.expiresAt.isAfter(now.plusSeconds(60))) {
            return@withLock current.token
        }
        val fresh = requestInstallationToken()
        cached = fresh
        fresh.token
    }

    private suspend fun requestInstallationToken(): InstallationToken {
        val jwt = signJwt()
        val url = "${config.apiUrl}/app/installations/${config.installationId}/access_tokens"
        appLog.debug("requesting GitHub installation token from {}", url)
        val response: InstallationTokenResponse = client.post(url) {
            bearerAuth(jwt)
            headers {
                append(HttpHeaders.Accept, "application/vnd.github+json")
                append("X-GitHub-Api-Version", "2022-11-28")
            }
        }.body()
        return InstallationToken(
            token = response.token,
            expiresAt = Instant.parse(response.expires_at),
        )
    }

    private fun signJwt(): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(config.appId)
            .withIssuedAt(Date.from(now.minusSeconds(30)))
            .withExpiresAt(Date.from(now.plusSeconds(540)))
            .sign(Algorithm.RSA256(null, privateKey))
    }

    private fun parsePrivateKey(pem: String): RSAPrivateKey {
        require(pem.isNotBlank()) { "GITHUB_APP_PRIVATE_KEY is empty" }
        val base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.getDecoder().decode(base64)
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateKey
    }

    private data class InstallationToken(val token: String, val expiresAt: Instant)

    @Suppress("PropertyName", "ConstructorParameterNaming")
    private data class InstallationTokenResponse(
        val token: String,
        val expires_at: String,
    )
}
