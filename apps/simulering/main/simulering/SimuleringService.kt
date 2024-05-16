@file:Suppress("NAME_SHADOWING")

package simulering

import com.ctc.wstx.exc.WstxEOFException
import com.ctc.wstx.exc.WstxIOException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.plugins.logging.*
import jakarta.xml.ws.WebServiceException
import jakarta.xml.ws.soap.SOAPFaultException
import kotlinx.coroutines.runBlocking
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.appLog
import libs.utils.secureLog
import libs.ws.*
import simulering.dto.SimuleringRequestBody
import simulering.dto.SimuleringRequestBuilder
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

private object SimulerAction {
    private const val HOST = "http://nav.no"
    private const val PATH = "system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
    private const val SERVICE = "simulerFpService"
    const val BEREGNING = "$HOST/$PATH/$SERVICE/simulerBeregningRequest"
    const val SEND_OPPDRAG = "$HOST/$PATH/$SERVICE/sendInnOppdragRequest"
}

class SimuleringService(private val config: Config) {
    private val http = HttpClientFactory.new(LogLevel.ALL)
    private val azure = AzureTokenProvider(config.azure)
    private val sts = StsClient(config.simulering.sts, http, proxyAuth = ::getAzureTokenAsync)
    private val soap = SoapClient(config.simulering, sts, http, proxyAuth = ::getAzureToken)

    suspend fun simuler(request: SimuleringRequestBody): Simulering {
        val request = SimuleringRequestBuilder(request).build()
        val xml = xmlMapper.writeValueAsString(request.request)
        val response = soap.call(SimulerAction.BEREGNING, xml)
        return json(response).intoDto()
    }

    private fun json(xml: String): SimuleringResponse.SimulerBeregningResponse.Response.Beregning {
        try {
            secureLog.info("Forsøker å deserialisere simulering")
            val wrapper = simulerBeregningResponse(xml)
            return wrapper.response.simulering
        } catch (e: Throwable) {
            secureLog.info("feilet deserializering av simulering", e)
            fault(xml)
        }
    }

    private fun simulerBeregningResponse(xml: String): SimuleringResponse.SimulerBeregningResponse = runCatching {
        tryInto<SimuleringResponse>(xml).simulerBeregningResponse
    }.getOrElse {
        throw soapError("Failed to deserialize soap message: ${it.message}", it)
    }

    // denne kaster exception oppover i call-stacken
    private fun fault(xml: String): Nothing {
        try {
            secureLog.info("Forsøker å deserialisere fault")
            throw soapError(tryInto<SoapFault>(xml).fault)
        } catch (e: Throwable) {
            throw when (e) {
                is SoapException -> expand(e)
                else -> {
                    appLog.error("feilet deserializering av fault")
                    secureLog.error("feilet deserializering av fault", e)
                    soapError("Ukjent feil ved simulering: ${e.message}", e)
                }
            }
        }
    }

    private inline fun <reified T> tryInto(xml: String): T {
        val res = xmlMapper.readValue<SoapResponse<T>>(xml)
        return res.body
    }

    private fun expand(e: SoapException): RuntimeException {
        secureLog.info("expands soapfault", e)
        return with(e.msg) {
            when {
                contains("Personen finnes ikke") -> PersonFinnesIkkeException(this)
                contains("ugyldig") -> RequestErUgyldigException(this)
                else -> e
            }
        }
    }

    private fun soapFault(ex: SOAPFaultException) {
        logSoapFaultException(ex)

        when (ex.cause) {
            is WstxEOFException, is WstxIOException -> throw OppdragErStengtException()
            else -> throw ex
        }
    }

    private fun webserviceFault(ex: WebServiceException) {
        when (ex.cause) {
            is SSLException, is SocketException, is SocketTimeoutException -> throw OppdragErStengtException()
            else -> throw ex
        }
    }

    private suspend fun getAzureTokenAsync(): String {
        return "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
    }

    private fun getAzureToken(): String {
        return runBlocking {
            "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
        }
    }
}

private val xmlMapper: ObjectMapper =
    XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) })
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

class PersonFinnesIkkeException(feilmelding: String) : RuntimeException(feilmelding)
class RequestErUgyldigException(feilmelding: String) : RuntimeException(feilmelding)
class OppdragErStengtException : RuntimeException("Oppdrag/UR er stengt")

private fun logSoapFaultException(e: SOAPFaultException) {
    val details = e.fault.detail
        ?.detailEntries
        ?.asSequence()
        ?.mapNotNull { it.textContent }
        ?.joinToString(",")

    secureLog.error(
        """
            SOAPFaultException -
                faultCode=${e.fault.faultCode}
                faultString=${e.fault.faultString}
                details=$details
        """.trimIndent()
    )
}
