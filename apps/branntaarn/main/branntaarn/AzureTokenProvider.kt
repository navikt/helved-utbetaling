package branntaarn

import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import libs.utils.appLog
import models.unavailable
import org.http4k.core.*
import libs.utils.env

data class AzureConfig(
    val tokenEndpoint: URL = env("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = env("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = env("AZURE_APP_CLIENT_SECRET"),
)

class AzureTokenProvider(
    private val config: AzureConfig,
    private val http: HttpHandler,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val cache: TokenCache<AzureToken> = TokenCache(),
) {
    fun getClientCredentialsToken(scope: String): AzureToken {
        cache.get(scope)?.let { return it }

        val body = listOf(
            "client_id=${config.clientId}",
            "client_secret=${config.clientSecret}",
            "scope=$scope",
            "grant_type=client_credentials",
        ).joinToString("&")

        val request = Request(Method.POST, config.tokenEndpoint.toString())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .body(body)
        val response = http(request)

        if (!response.status.successful) {
            appLog.warn("Token provider returnerte HTTP ${response.status.code}")
            if (response.status.code in 400..499 && response.status.code != 429) {
                unavailable("Token provider avviste client credentials")
            }
            unavailable("Token provider er utilgjengelig")
        }

        val token = json.decodeFromString<AzureToken>(response.bodyString())
        cache.add(scope, token)
        return token
    }
}

interface Token {
    fun isExpired(): Boolean
}

@Serializable
data class AzureToken(
    val expires_in: Long,
    val access_token: String,
) : Token {
    @kotlinx.serialization.Transient
    private val expiry: Instant = Instant.now().plusSeconds(expires_in - 60)

    override fun isExpired(): Boolean = Instant.now().isAfter(expiry)
}

class TokenCache<T : Token> {
    private val tokens = ConcurrentHashMap<String, T>()

    fun get(key: String): T? {
        val token = tokens[key] ?: return null
        if (token.isExpired()) {
            tokens.remove(key)
            return null
        }
        return token
    }

    fun add(key: String, token: T) {
        tokens[key] = token
    }

    fun rm(key: String) {
        tokens.remove(key)
    }
}
