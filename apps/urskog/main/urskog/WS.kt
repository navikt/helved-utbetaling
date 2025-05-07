package urskog

import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.plugins.logging.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.secureLog
import libs.ws.*
import libs.xml.XMLMapper
import models.*
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
        val xml = stripEnvelope(response) ?: response.intoFault()
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

private val faultMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) })
private fun String.intoFault(): Nothing {
    val fault = runCatching { faultMapper.readValue<SoapResponse<SoapFault>>(this).body.fault }.getOrNull()
    if (fault == null) panic(this)
    when {
        fault.faultstring.contains("Personen finnes ikke") -> notFound(this)
        fault.faultstring.contains("ugyldig") -> badRequest(this)
        fault.faultstring.contains("simulerBeregningFeilUnderBehandling") -> resolveBehandlingFault(fault)
        fault.faultstring.contains("Conversion to SOAP failed") -> resolveSoapConversionFailure(fault)
        else -> panic(fault)
    }
}

private fun panic(any: Any): Nothing {
    wsLog.error("ukjent soap feil {}", any)
    secureLog.error("ukjent soap feil {}", any)
    internalServerError("ukjent soap feil")
}

private fun resolveBehandlingFault(fault: Fault): Nothing {
    val detail = fault.detail ?: panic(fault)
    val feilUnderBehandling = detail["simulerBeregningFeilUnderBehandling"] as? Map<*, *> ?: panic(fault)
    val errorMessage = feilUnderBehandling["errorMessage"] as? String ?: panic(fault)
    when {
        errorMessage.contains("OPPDRAGET/FAGSYSTEM-ID finnes ikke fra før") -> notFound("SakId ikke funnet")
        errorMessage.contains("DELYTELSE-ID/LINJE-ID ved endring finnes ikke") -> unprocessable("Referert utbetaling finnes ikke og kan ikke simuleres på. Mulig årsak er at det simuleres på en utbetaling som ikke er utbetalt enda, prøv igjen neste virkedag.")
        errorMessage.contains("Oppdraget finnes fra før") -> conflict("Utbetaling med SakId/BehandlingId finnes fra før")
        errorMessage.contains("Referert vedtak/linje ikke funnet") -> notFound("Endret utbetalingsperiode refererer ikke til en eksisterende utbetalingsperiode")
        errorMessage.contains("Navn på person ikke funnet i PDL") -> notFound("Navn på person ikke funnet i PDL")
        errorMessage.contains("Personen finnes ikke i PDL") -> notFound("Personen finnes ikke i PDL")
        else -> panic(errorMessage)
    }
}

private fun resolveSoapConversionFailure(fault: Fault): Nothing {
    val detail = fault.detail ?: panic(fault)
    val cicsFault = detail["CICSFault"]?.toString() ?: panic(fault)
    when {
        cicsFault.contains("DFHPI1008") -> forbidden("ConsumerId (service-user) er ikke gyldig for simuleringstjenesten. Det kan ha vært datalast i Oppdragsystemet. Kontakt oss eller PO Utbetaling.")
        else -> panic(fault)
    }
}
