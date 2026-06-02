package simulering

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import libs.utils.env
import libs.utils.logger
import libs.utils.secureLog
import org.http4k.core.*
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

private val authLog = logger("auth")

private val json = Json { ignoreUnknownKeys = true }

data class AzureConfig(
    val tokenEndpoint: URL = env("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val jwks: URL = env("AZURE_OPENID_CONFIG_JWKS_URI"),
    val issuer: String = env("AZURE_OPENID_CONFIG_ISSUER"),
    val clientId: String = env("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = env("AZURE_APP_CLIENT_SECRET"),
)

class AzureTokenProvider(
    private val config: AzureConfig,
    private val http: HttpHandler,
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

        if (response.status.code !in 200..299) {
            authLog.warn("Failed to get token from Azure AD")
            secureLog.warn("Got HTTP ${response.status.code} from ${config.tokenEndpoint}: ${response.bodyString()}")
            error("Failed to get token from Azure AD: ${config.tokenEndpoint}")
        }

        val token = json.decodeFromString<AzureToken>(response.bodyString())
        cache.add(scope, token)
        return token
    }
}

interface Token {
    fun isExpired(): Boolean
}

data class SamlToken(
    val token: String,
    val expirationTime: LocalDateTime,
) : Token {
    override fun isExpired(): Boolean =
        expirationTime <= LocalDateTime.now().plus(Duration.ofSeconds(10))
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
