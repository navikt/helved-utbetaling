package libs.ws

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import libs.http.HttpClientFactory
import libs.utils.Resource
import java.net.URL
import java.util.*

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
    private val http: HttpClient = HttpClientFactory.new(),
) : Soap {
    override suspend fun call(
        action: String,
        body: String,
    ): String {
        val token = sts.samlToken()

        val xml = SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = config.host,
            assertion = token.token,
            body = body,
        )

        val res = http.post(config.host) {
            contentType(ContentType.Application.Xml)
            header("SOAPAction", action)
            setBody(xml)
        }

        return res.bodyAsText()
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
        .replace("\$serviceUrl", serviceUrl.toString())
        .replace("\$assertion", assertion)
        .replace("\$body", body)
}

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
data class SoapResponse<T>(
    @JacksonXmlProperty(localName = "Header")
    val header: SoapHeader?,
    @JacksonXmlProperty(localName = "Body")
    val body: T,
)

data class SoapHeader(
    @JacksonXmlProperty(localName = "Action", namespace = "http://www.w3.org/2005/08/addressing")
    val action: String,
    @JacksonXmlProperty(localName = "MessageID", namespace = "http://www.w3.org/2005/08/addressing")
    val messageId: String,
)

data class SoapFault(
    @JacksonXmlProperty(localName = "Fault", namespace = "http://www.w3.org/2003/05/soap-envelope")
    val fault: Fault
)

data class Fault(
    @JacksonXmlProperty(localName = "faultcode")
    val code: String,
    @JacksonXmlProperty(localName = "faultstring")
    val messsage: String,
)

class SoapException(
    val msg: String,
    val code: String? = null,
    val details: String? = null,
    ex: Throwable? = null,
) : RuntimeException(msg, ex)

fun soapError(msg: String, ex: Throwable) = SoapException(msg, ex = ex)

fun soapError(fault: Fault) = SoapException(
    msg = """
        SOAP fault.
        Code: ${fault.code}
        Message: ${fault.messsage}
        """.trimIndent(),
    code = fault.code,
)
