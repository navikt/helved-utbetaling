package libs.ws

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import libs.http.HttpClientFactory
import libs.utils.Resource
import libs.utils.logger
import models.badGateway
import java.net.URL
import java.util.*

val wsLog = logger("ws")

interface Soap {
    suspend fun call(action: String, body: String): String
}

data class SoapConfig(
    val host: URL,
    val sts: StsConfig,
)

class SoapClient(
    private val config: SoapConfig,
    private val sts: Sts,
    private val http: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val proxyAuth: ProxyAuthProvider? = null,
) : Soap {
    override suspend fun call(
        action: String,
        body: String,
    ): String {
        val token = sts.samlToken()
        val proxyAuth = proxyAuth?.invoke()

        val xml = SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = config.host,
            assertion = token.token,
            body = body,
        )

        val res = http.post(config.host) {
            proxyAuth?.let { header("X-Proxy-Authorization", it) }
            header("Content-Type", "text/xml")
            header("Accept", "*/*")
            setBody(xml)
        }

        return res.tryInto()
    }
}

private suspend fun HttpResponse.tryInto(): String {
    when (status) {
        HttpStatusCode.InternalServerError, HttpStatusCode.OK -> return bodyAsText()
        HttpStatusCode.BadGateway -> badGateway("simulering stengt, tilgjengelig man-fre kl 6-21")
        else -> error("Unexpected status code: $status when calling ${request.url}")
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

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
data class SoapResponse<T>(
    @param:JacksonXmlProperty(localName = "Header")
    val header: SoapHeader?,
    @param:JacksonXmlProperty(localName = "Body")
    val body: T,
)

data class SoapHeader(
    @param:JacksonXmlProperty(localName = "Action", namespace = "http://www.w3.org/2005/08/addressing")
    val action: String,
    @param:JacksonXmlProperty(localName = "MessageID", namespace = "http://www.w3.org/2005/08/addressing")
    val messageId: String,
)

data class SoapFault(
    @param:JacksonXmlProperty(localName = "Fault", namespace = "http://www.w3.org/2003/05/soap-envelope")
    val fault: Fault
)

data class Fault(
    val faultcode: String,
    val faultstring: String,
    val detail: Map<String, Any> = emptyMap(),
)

class SoapException(msg: String) : RuntimeException(msg)

fun soapError(fault: Fault): Nothing = throw SoapException(
    """
        SOAP fault.
        Code: ${fault.faultcode}
        Message: ${fault.faultstring}
        Detail: ${fault.detail}
    """.trimIndent(),
)
