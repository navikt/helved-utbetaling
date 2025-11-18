package urskog

import io.ktor.client.plugins.logging.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.ws.Fault
import libs.ws.SoapClient
import libs.ws.StsClient
import libs.ws.wsLog
import libs.xml.XMLMapper
import models.badRequest
import models.notFound
import models.unprocessable
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse

class SimuleringService(private val config: Config) {
    private val http = HttpClientFactory.new(LogLevel.ALL)
    private val azure = AzureTokenProvider(config.azure)
    private val sts = StsClient(config.simulering.sts, http, proxyAuth = ::getAzureToken)
    private val soap = SoapClient(config.simulering, sts, http, proxyAuth = ::getAzureToken)
    private val requestMapper: XMLMapper<SimulerBeregningRequest> = XMLMapper(false)
    private val responseMapper: XMLMapper<SimulerBeregningResponse> = XMLMapper(false)

    suspend fun simuler(request: SimulerBeregningRequest): SimulerBeregningResponse {
        val response = soap.call(SimulerAction.BEREGNING, requestMapper.writeValueAsString(request))
        val xml = stripEnvelope(response) ?: response.intoFault().panic()
        return responseMapper.readValue(xml)
    }

    private suspend fun getAzureToken(): String {
        return "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
    }

    private object SimulerAction {
        private const val HOST = "http://nav.no"
        private const val PATH = "system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
        private const val SERVICE = "simulerFpService"
        const val BEREGNING = "$HOST/$PATH/$SERVICE/simulerBeregning"
    }
}

fun stripEnvelope(input: String): String? {
    val start = "<simulerBeregningResponse"
    val end = "</simulerBeregningResponse>"
    val startIdx = input.indexOf(start)
    val endIdx = input.indexOf(end) + end.length
    if (startIdx == -1 || endIdx == -1) return null
    return input.substring(startIdx, endIdx)
}

private fun String.intoFault(): Fault {
    val builder = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
    val document = builder.parse(java.io.ByteArrayInputStream(this.toByteArray(Charsets.UTF_8)))
    val fault = document.getElementsByTagName("SOAP-ENV:Fault").item(0) as org.w3c.dom.Element
    val faultcode = fault.getElementsByTagName("faultcode").item(0).textContent.trim()
    val faultstring = fault.getElementsByTagName("faultstring").item(0).textContent.trim()
    val detailElement = fault.getElementsByTagName("detail")?.item(0) as? org.w3c.dom.Element

    fun toMap(element: org.w3c.dom.Element): Map<String, Any> {
        val res = mutableMapOf<String, Any>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is org.w3c.dom.Element) {
                val childMap = toMap(node)
                if (childMap.isEmpty()) {
                    res[node.tagName] = node.textContent.trim()
                }  else {
                    res[node.tagName] = childMap
                }
            }
        }
        return res
    }

    val detail = detailElement?.let(::toMap) ?: emptyMap()
    return Fault(faultcode, faultstring, detail)
}

private fun Fault.panic(): Nothing {
    when (faultstring) {
        "simulerBeregningFeilUnderBehandling" -> {
            detail["sf:simulerBeregningFeilUnderBehandling"]?.let {
                @Suppress("UNCHECKED_CAST")
                val map = it as Map<String, String>
                val errorMessage = map["errorMessage"] ?: panic(this)
                unprocessable(errorMessage)
            }
        }
        "Conversion from SOAP failed" -> {
            detail["CICSFault"]?.let { badRequest(it as String) }
        }
        "Personen finnes ikke"  -> notFound("Personen finnes ikke")
        else -> panic(this) 
    }
    panic(this) // fallback
}

private fun panic(any: Any): Nothing {
    wsLog.error("ukjent soap feil {}", any)
    // secureLog.error("ukjent soap feil {}", any)
    badRequest("ukjent soap feil $any")
}

