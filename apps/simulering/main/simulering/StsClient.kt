package simulering

import kotlinx.serialization.json.*
import libs.utils.secureLog
import org.http4k.core.*
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

        val json = Json.parseToJsonElement(response.bodyString())

        val accessToken = json.jsonObject["access_token"]?.jsonPrimitive?.contentOrNull
            ?: stsError(json)
        val tokenType = json.jsonObject["issued_token_type"]?.jsonPrimitive?.contentOrNull
            ?: stsError(json)
        val expiresIn = json.jsonObject["expires_in"]?.jsonPrimitive?.longOrNull
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

fun stsError(element: JsonElement): Nothing = throw StsException(
    """
        Error from STS: ${element.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: ""}
        Details: ${element.jsonObject["detail"]?.jsonPrimitive?.contentOrNull ?: element}
    """.trimIndent()
)
