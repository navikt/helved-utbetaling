package simulering

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import libs.utils.secureLog
import org.http4k.core.*
import org.http4k.filter.ClientFilters
import java.net.URL
import java.time.LocalDateTime
import java.util.*

interface Sts {
    fun samlToken(): SamlToken
    fun invalidate()
}

data class StsConfig(
    val host: URL,
    val user: String,
    val pass: String,
)

class StsClient(
    private val config: StsConfig,
    private val http: HttpHandler,
    private val jackson: ObjectMapper = jacksonObjectMapper(),
    private val cache: TokenCache<SamlToken> = TokenCache(),
    private val proxyAuth: (() -> String)? = null,
) : Sts {

    override fun samlToken(): SamlToken {
        cache.get(config.user)?.let { return it }

        val request = Request(Method.GET, "${config.host}/rest/v1/sts/samltoken")
            .header("Authorization", Credentials(config.user, config.pass).toBasicAuth())
            .let { req -> proxyAuth?.let { req.header("X-Proxy-Authorization", it()) } ?: req }

        val response = http(request)

        if (response.status != Status.OK) {
            secureLog.error("STS feil: ${response.status} ${response.bodyString()}")
            error("Unexpected status ${response.status} from STS")
        }

        val json = jackson.readTree(response.bodyString())

        val accessToken = json["access_token"]?.takeIf(JsonNode::isTextual)?.asText()
            ?: stsError(json)
        val tokenType = json["issued_token_type"]?.takeIf(JsonNode::isTextual)?.asText()
            ?: stsError(json)
        val expiresIn = json["expires_in"]?.takeIf(JsonNode::isNumber)?.asLong()
            ?: stsError(json)

        if (tokenType != "urn:ietf:params:oauth:token-type:saml2") stsError(json)

        val decoded = String(Base64.getDecoder().decode(accessToken))
        val token = SamlToken(decoded, LocalDateTime.now().plusSeconds(expiresIn))
        cache.add(config.user, token)
        return token
    }

    override fun invalidate() {
        cache.rm(config.user)
    }
}

private fun Credentials.toBasicAuth(): String {
    val encoded = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
    return "Basic $encoded"
}

class StsException(msg: String) : RuntimeException(msg)

fun stsError(node: JsonNode): Nothing = throw StsException(
    """
        Error from STS: ${node.path("title").asText()}
        Details: ${node.path("detail").takeIf(JsonNode::isTextual) ?: node}
    """.trimIndent()
)

