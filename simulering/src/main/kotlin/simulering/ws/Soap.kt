package simulering.ws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.intellij.lang.annotations.Language
import simulering.http.HttpClientFactory
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
//    private val proxyAuth: (() -> String),
    private val http: HttpClient = HttpClientFactory.create(),
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
            body
        )

        val res = http.post(config.host) {
            contentType(ContentType.Application.Xml)
            header("SOAPAction", action)
//            header("X-Proxy-Authorization", proxyAuth())
            setBody(xml)
        }

        return res.bodyAsText()
    }
}

private object SoapXml {
    @Language("XML")
    fun envelope(action: String, messageId: UUID, serviceUrl: URL, assertion: String, body: String, ): String = """
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
            <soap:Header>
                <Action xmlns="http://www.w3.org/2005/08/addressing">$action</Action>
                <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:$messageId</MessageID>
                <To xmlns="http://www.w3.org/2005/08/addressing">$serviceUrl</To>
                <ReplyTo xmlns="http://www.w3.org/2005/08/addressing">
                    <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>
                </ReplyTo>
                <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" soap:mustUnderstand="1">$assertion</wsse:Security>
            </soap:Header>
            <soap:Body>$body</soap:Body>
        </soap:Envelope>
        """.trimIndent()
}

inline fun <reified T> XmlMapper.deserializeSoap(body: String): T {
    return runCatching {
        val soap = this.readValue<SoapResponse<T>>(body)
        requireNotNull(soap.body)
    }.onFailure {
        runCatching {
            this.readValue<SoapResponse<SoapFault>>(body).body.fault
        }.onSuccess { fault ->
            soapError(fault)
        }
        soapError("Failed to deserialize soap messag: ${it.message}", it)
    }.getOrThrow()
}

data class SoapHeader(
    @JacksonXmlProperty(localName = "Action", namespace = "http://www.w3.org/2005/08/addressing")
    val action: String,
    @JacksonXmlProperty(localName = "MessageID", namespace = "http://www.w3.org/2005/08/addressing")
    val messageId: String,
    @JacksonXmlProperty(localName = "RelatesTo", namespace = "http://www.w3.org/2005/08/addressing")
    val relatesTo: String
)

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
data class SoapResponse<T>(
    @JacksonXmlProperty(localName = "Header")
    val header: SoapHeader?,
    @JacksonXmlProperty(localName = "Body")
    val body: T
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
    @JacksonXmlProperty(localName = "detail")
    val detail: JsonNode?
)

class SoapException(msg: String, ex: Throwable? = null) : RuntimeException(msg, ex)

fun soapError(msg: String, ex: Throwable): Nothing {
    throw SoapException(msg, ex)
}

fun soapError(fault: Fault): Nothing {
    throw SoapException(
        """
            SOAP fault.
            Code: ${fault.code}
            Message: ${fault.messsage}
            Details: ${fault.detail?.toPrettyString()}
        """.trimIndent()
    )
}
