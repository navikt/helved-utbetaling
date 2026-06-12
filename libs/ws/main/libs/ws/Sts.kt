package libs.ws

import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import libs.cache.Token
import libs.cache.TokenCache
import libs.http.HttpClientFactory
import libs.utils.secureLog
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

interface Sts {
    val http: HttpClient
    suspend fun samlToken(): SamlToken
}

data class StsConfig(
    val host: URL,
    val user: String,
    val pass: String,
)

typealias ProxyAuthProvider = suspend () -> String

class StsClient(
    private val config: StsConfig,
    private val json: Json,
    override val http: HttpClient = HttpClientFactory.new(json, LogLevel.ALL),
    private val cache: TokenCache<SamlToken> = TokenCache(),
    private val proxyAuth: ProxyAuthProvider? = null,
) : Sts {
    override suspend fun samlToken(): SamlToken {
        val token = cache.get(config.user)
        if (token != null) {
            return token
        }

        val response = http.get("${config.host}/rest/v1/sts/samltoken") {
            basicAuth(config.user, config.pass)
            proxyAuth?.let { it -> header("X-Proxy-Authorization", it()) }
        }

        val samlToken = response.tryInto {
            val accessToken = it.jsonObject["access_token"]
                ?.jsonPrimitive?.contentOrNull
                ?: stsError(it)

            val tokenType = it.jsonObject["issued_token_type"]
                ?.jsonPrimitive?.contentOrNull
                ?: stsError(it)

            val expiresIn = it.jsonObject["expires_in"]
                ?.jsonPrimitive?.longOrNull
                ?: stsError(it)

            if (tokenType != "urn:ietf:params:oauth:token-type:saml2") {
                stsError(it)
            }

            val decoded = String(Base64.getDecoder().decode(accessToken))

            SamlToken(
                token = decoded,
                expirationTime = LocalDateTime.now().plusSeconds(expiresIn)
            )
        }

        cache.add(config.user, samlToken)
        return samlToken
    }

    suspend fun invalidate() {
        cache.rm(config.user)
    }

    private suspend fun <T : Any> HttpResponse.tryInto(from: (JsonElement) -> T): T {
        when (status) {
            HttpStatusCode.OK -> {
                val body = bodyAsText()
                val json = Json.parseToJsonElement(body)
                return from(json)
            }

            else -> {
                wsLog.error("Unexpected status code: $status when calling ${request.url}")
                secureLog.error("Unexpected status code: $status when calling ${request.url} ${bodyAsText()}")
                error("Unexpected status code: $status when calling ${request.url}")
            }
        }
    }

}

class StsException(msg: String) : RuntimeException(msg)

fun stsError(element: JsonElement): Nothing {
    throw StsException(
        """
            Error from STS: ${element.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: ""}
            Details: ${element.jsonObject["detail"]?.jsonPrimitive?.contentOrNull ?: element}
        """.trimIndent()
    )
}

data class SamlToken(
    val token: String,
    val expirationTime: LocalDateTime,
) : Token {

    override fun isExpired(): Boolean =
        expirationTime <= LocalDateTime.now().plus(EXP_LEEWAY)

    companion object {
        private val EXP_LEEWAY = Duration.ofSeconds(10)
    }
}
