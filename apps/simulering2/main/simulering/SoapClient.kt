package simulering

import libs.utils.Resource
import libs.utils.logger
import models.badGateway
import org.http4k.core.*
import java.net.URL
import java.util.*

val wsLog = logger("ws")

interface Soap {
    fun call(action: String, body: String): String
}

data class SoapConfig(
    val host: URL,
    val sts: StsConfig,
)

class SoapClient(
    private val config: SoapConfig,
    private val sts: Sts,
    private val http: HttpHandler,
    private val proxyAuth: (() -> String)? = null,
) : Soap {

    override fun call(action: String, body: String): String {
        val token = sts.samlToken()

        val xml = SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = config.host,
            assertion = token.token,
            body = body,
        )

        val request = Request(Method.POST, config.host.toString())
            .header("Content-Type", "text/xml")
            .header("Accept", "*/*")
            .let { req -> proxyAuth?.let { req.header("X-Proxy-Authorization", it()) } ?: req }
            .body(xml)

        val response = http(request)

        return when (response.status) {
            Status.INTERNAL_SERVER_ERROR, Status.OK -> response.bodyString()
            Status.BAD_GATEWAY -> badGateway("simulering stengt, tilgjengelig man-fre kl 6-21")
            else -> error("Unexpected status ${response.status} from ${config.host}")
        }
    }
}

object SoapXml {
    fun envelope(
        action: String,
        messageId: UUID,
        serviceUrl: URL,
        assertion: String,
        body: String,
    ): String = Resource.read("/envelope.xml")
        .replace("\$action", action)
        .replace("\$messageId", messageId.toString())
        .replace("\$serviceUrl", "https://cics-q1.adeo.no/oppdrag/simulerFpServiceWSBinding") // FIXME: production ready
        .replace("\$assertion", assertion)
        .replace("\$body", body)
}

class SoapException(msg: String) : RuntimeException(msg)

fun soapError(fault: Fault): Nothing = throw SoapException(
    """
        SOAP fault.
        Code: ${fault.faultcode}
        Message: ${fault.faultstring}
        Detail: ${fault.detail}
    """.trimIndent(),
)
