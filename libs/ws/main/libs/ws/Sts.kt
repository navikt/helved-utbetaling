package libs.ws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import libs.cache.Token
import libs.cache.TokenCache
import libs.http.HttpClientFactory
import libs.utils.secureLog

interface Sts {
    suspend fun samlToken(): SamlToken
}

data class StsConfig(
    val host: URL,
    val user: String,
    val pass: String,
)

typealias ProxyAuthProvider = suspend () -> String

private val simpleHttpClient: HttpClient = HttpClientFactory.new(LogLevel.ALL, null, null, null)

class StsClient(
    private val config: StsConfig,
    private val http: HttpClient = simpleHttpClient,
    private val jackson: ObjectMapper = jacksonObjectMapper(),
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
            val accessToken = it["access_token"]
                ?.takeIf(JsonNode::isTextual)?.asText()
                ?: stsError(it)

            val tokenType = it["issued_token_type"]
                ?.takeIf(JsonNode::isTextual)?.asText()
                ?: stsError(it)

            val expiresIn = it["expires_in"]
                ?.takeIf(JsonNode::isNumber)?.asLong()
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

    private suspend fun <T : Any> HttpResponse.tryInto(from: (JsonNode) -> T): T {
        when (status) {
            HttpStatusCode.OK -> {
                val body = bodyAsText()
                val json = jackson.readTree(body)
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

fun stsError(node: JsonNode): Nothing {
    throw StsException(
        """
            Error from STS: ${node.path("title").asText()}
            Details: ${node.path("detail").takeIf(JsonNode::isTextual) ?: node}
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
