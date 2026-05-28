package simulering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import libs.utils.logger
import libs.utils.secureLog
import org.http4k.core.*
import java.net.URL
import java.util.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

private val authLog = logger("auth")

data class AzureConfig(
    val tokenEndpoint: URL,
    val jwks: URL,
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
)

class AzureTokenProvider(
    private val config: AzureConfig,
    private val http: HttpHandler,
    private val cache: TokenCache<AzureToken> = TokenCache(),
) {
    private val jackson = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

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

        val token = jackson.readValue<AzureToken>(response.bodyString())
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

data class AzureToken(
    val expires_in: Long,
    val access_token: String,
) : Token {
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

