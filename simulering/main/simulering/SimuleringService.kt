@file:Suppress("NAME_SHADOWING")

package simulering

import com.ctc.wstx.exc.WstxEOFException
import com.ctc.wstx.exc.WstxIOException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.xml.ws.WebServiceException
import jakarta.xml.ws.soap.SOAPFaultException
import libs.utils.secureLog
import libs.ws.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.intellij.lang.annotations.Language
import simulering.dto.SimuleringRequestBody
import simulering.dto.SimuleringRequestBuilder
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.LocalDate
import javax.net.ssl.SSLException

private object SimulerAction {
    private const val HOST = "http://nav.no"
    private const val PATH = "system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
    private const val SERVICE = "simulerFpService"
    const val BEREGNING = "$HOST/$PATH/$SERVICE/simulerBeregningRequest"
    const val SEND_OPPDRAG = "$HOST/$PATH/$SERVICE/sendInnOppdragRequest"
}

class SimuleringService(
    private val client: Soap,
    private val xmlMapper: XmlMapper = XmlMapper().apply {
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    },
) {

    // todo: kan/når er svar fra simulering tom?
    suspend fun simuler(request: SimuleringRequestBody): Simulering? {
        val request = SimuleringRequestBuilder(request).build()
        val xml = xml(request.request)

        val response = client.call(
            action = SimulerAction.BEREGNING,
            body = xml,
        )

        val json = json(response)
        val simulering = simulering(json)
        return simulering
    }

    private fun json(xml: String): JsonNode {
        try {
            secureLog.info("Forsøker å deserialisere simulering")
            return tryInto<JsonNode>(xml).also {
                if (it.has("Fault")) {
                    error("simulering sin body inneholder en fault")
                }
            }
        } catch (e: Throwable) {
            secureLog.warn("Forsøker å deserialisere fault", e)
            failure(xml)
        }
    }

    private fun failure(xml: String): Nothing {
        try {
            secureLog.warn("Forsøker å deserialisere fault")
            val soapFault = tryInto<SoapFault>(xml)
            throw soapError(soapFault.fault)
        } catch (e: Throwable) {
            throw when (e) {
                is SoapException -> expload(e)
                else -> {
                    secureLog.error("Klarte ikke å deserialisere fault", e)
                    soapError("Ukjent feil ved simulering: ${e.message}", e)
                }
            }
        }
    }

    private inline fun <reified T> tryInto(xml: String): T {
        return runCatching {
            val soap = xmlMapper.readValue<SoapResponse<T>>(xml)
            requireNotNull(soap.body)
        }.getOrElse {
            throw soapError("Failed to deserialize soap message: ${it.message}", it)
        }
    }

    // se SimulerBeregningResponse?.tilSimulering() i Simulering.kt
    private fun simulering(json: JsonNode): Simulering {
        // todo: returner null eller kast exception ved feil?
        val simulering = json["simulerBeregningResponse"]["response"]["simulering"]

        return Simulering(
            gjelderId = simulering["gjelderId"].asText(),
            gjelderNavn = simulering["gjelderNavn"].asText(),
            datoBeregnet = simulering["datoBeregnet"].asText().let(LocalDate::parse),
            totalBelop = simulering["totalBelop"].asInt(),
            perioder = simulering["beregningsPeriode"].map { periode ->
                SimulertPeriode(
                    fom = periode["periodeFom"].asText().let(LocalDate::parse),
                    tom = periode["periodeTom"].asText().let(LocalDate::parse),
                    utbetalinger = listOf(
                        periode["beregningStoppnivaa"].let { utbetaling ->
                            Utbetaling(
                                fagSystemId = utbetaling["fagsystemId"].asText(),
                                utbetalesTilId = utbetaling["utbetalesTilId"].asText().removePrefix("00"),
                                utbetalesTilNavn = utbetaling["utbetalesTilNavn"].asText(),
                                forfall = utbetaling["forfall"].asText().let(LocalDate::parse),
                                feilkonto = utbetaling["feilkonto"].asBoolean(),
                                detaljer = utbetaling["beregningStoppnivaaDetaljer"].map { detalj ->
                                    Detaljer(
                                        faktiskFom = detalj["faktiskFom"].asText().let(LocalDate::parse),
                                        faktiskTom = detalj["faktiskTom"].asText().let(LocalDate::parse),
                                        konto = detalj["kontoStreng"].asText().trim(),
                                        belop = detalj["belop"].asInt(),
                                        tilbakeforing = detalj["tilbakeforing"].asBoolean(),
                                        sats = detalj["sats"].asDouble(),
                                        typeSats = detalj["typeSats"].asText().trim(), // kan være empty
                                        antallSats = detalj["antallSats"].asInt(),
                                        uforegrad = detalj["uforeGrad"].asInt(),
                                        utbetalingsType = detalj["typeKlasse"].asText(),
                                        klassekode = detalj["klassekode"].asText().trim(),
                                        klassekodeBeskrivelse = detalj["klasseKodeBeskrivelse"].asText().trim(),
                                        refunderesOrgNr = detalj["refunderesOrgNr"].asText().removePrefix("00"),
                                    )
                                }
                            )
                        }
                    )
                )
            }
        )
    }

    private fun expload(e: SoapException): RuntimeException {
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
}


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

@Language("XML")
private fun xml(request: SimulerBeregningRequest): String {
    return """<ns2:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
                             >
    <request>
        <simuleringsPeriode>
            <datoSimulerFom>${request.simuleringsPeriode.datoSimulerFom}</datoSimulerFom>
            <datoSimulerTom>${request.simuleringsPeriode.datoSimulerTom}</datoSimulerTom>
        </simuleringsPeriode>
        <oppdrag>
            <kodeEndring>${request.oppdrag.kodeEndring}</kodeEndring>
            <kodeFagomraade>${request.oppdrag.kodeFagomraade}</kodeFagomraade>
            <fagsystemId>${request.oppdrag.fagsystemId}</fagsystemId>
            <utbetFrekvens>${request.oppdrag.utbetFrekvens}</utbetFrekvens>
            <oppdragGjelderId>${request.oppdrag.oppdragGjelderId}</oppdragGjelderId>
            <datoOppdragGjelderFom>${request.oppdrag.datoOppdragGjelderFom}</datoOppdragGjelderFom>
            <saksbehId>${request.oppdrag.saksbehId}</saksbehId>
            ${
        request.oppdrag.enhet.joinToString(separator = "\n") { enhet ->
            """<ns3:enhet>
                    <typeEnhet>${enhet.typeEnhet}</typeEnhet>
                    <enhet>${enhet.enhet}</enhet>
                    <datoEnhetFom>${enhet.datoEnhetFom}</datoEnhetFom>
                </ns3:enhet>"""
        }
    }
            ${
        request.oppdrag.oppdragslinje.joinToString(separator = "\n") { linje ->
            """<oppdragslinje>
                <kodeEndringLinje>${linje.kodeEndringLinje}</kodeEndringLinje>
                <delytelseId>${linje.delytelseId}</delytelseId>
                ${linje.refDelytelseId?.let { """<refDelytelseId>${linje.refDelytelseId}</refDelytelseId>""" } ?: ""}
                ${linje.refFagsystemId?.let { """<refFagsystemId>${linje.refFagsystemId}</refFagsystemId>""" } ?: ""}
                <kodeKlassifik>${linje.kodeKlassifik}</kodeKlassifik>
                ${linje.kodeStatusLinje?.let { """<kodeStatusLinje>${linje.kodeStatusLinje}</kodeStatusLinje>""" } ?: ""}
                ${linje.datoStatusFom?.let { """<datoStatusFom>${linje.datoStatusFom}</datoStatusFom>""" } ?: ""}
                <datoVedtakFom>${linje.datoVedtakFom}</datoVedtakFom>
                <datoVedtakTom>${linje.datoVedtakTom}</datoVedtakTom>
                <sats>${linje.sats}</sats>
                <fradragTillegg>${linje.fradragTillegg}</fradragTillegg>
                <typeSats>${linje.typeSats}</typeSats>
                <brukKjoreplan>${linje.brukKjoreplan}</brukKjoreplan>
                <saksbehId>${linje.saksbehId}</saksbehId>
                ${
                linje.utbetalesTilId?.let {
                    """<utbetalesTilId>$it</utbetalesTilId>"""
                } ?: ""
            }
                ${
                linje.grad.joinToString(separator = "\n") { grad ->
                    """<ns3:grad>
                    <typeGrad>${grad.typeGrad}</typeGrad>
                    ${grad.grad?.let { """<grad>${grad.grad}</grad>""" } ?: ""}
                </ns3:grad>"""
                }
            }
                ${
                linje.attestant.joinToString(separator = "\n") { attestant ->
                    """<ns3:attestant>
                    <attestantId>${attestant.attestantId}</attestantId>
                </ns3:attestant>"""
                }
            }
                ${
                linje.refusjonsInfo?.let { refusjonsInfo ->
                    """<ns3:refusjonsInfo>
                    <refunderesId>${refusjonsInfo.refunderesId}</refunderesId>
                    ${refusjonsInfo.maksDato?.let { """<maksDato>${refusjonsInfo.maksDato}</maksDato>""" } ?: ""}
                    <datoFom>${refusjonsInfo.datoFom}</datoFom>
                </ns3:refusjonsInfo>"""
                } ?: ""
            }
            </oppdragslinje>"""
        }
    }
        </oppdrag>
    </request>
</ns2:simulerBeregningRequest>"""
}