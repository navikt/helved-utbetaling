package simulering

import org.intellij.lang.annotations.Language
import simulering.ws.SamlToken
import simulering.ws.Soap
import simulering.ws.Sts
import java.net.URI
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class FakeSts : Sts {
    override suspend fun samlToken(): SamlToken {
        return SamlToken(
            "very-secure token",
            LocalDateTime.now(),
        )
    }
}

class FakeSoap : Soap {
    private fun resources(filename: String): String =
        {}::class.java.getResource(filename)!!.openStream().bufferedReader().readText()

    override suspend fun call(action: String, body: String): String {
        val body = resources("/responsXML_fagsak_10001_EFOG.xml")

        return SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = "http://localhost:8083".let(::URI).toURL(),
            assertion = "very-secure token",
            relatesTo = "",
            body = body,
        )
    }
}

private object SoapXml {
    @Language("XML")
    fun envelope(action: String, messageId: UUID, serviceUrl: URL, assertion: String, body: String, relatesTo: String): String = """
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
            <soap:Header>
                <Action xmlns="http://www.w3.org/2005/08/addressing">$action</Action>
                <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:$messageId</MessageID>
                <RelatesTo xmlns="http://www.w3.org/2005/08/addressing">$relatesTo</RelatesTo>
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