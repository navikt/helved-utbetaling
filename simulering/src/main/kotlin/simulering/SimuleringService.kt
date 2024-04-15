package simulering

import com.ctc.wstx.exc.WstxEOFException
import com.ctc.wstx.exc.WstxIOException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import felles.secureLog
import jakarta.xml.ws.WebServiceException
import jakarta.xml.ws.soap.SOAPFaultException
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.intellij.lang.annotations.Language
import simulering.dto.SimuleringRequestBody
import simulering.dto.SimuleringRequestBuilder
import simulering.ws.Soap
import simulering.ws.SoapResponse
import simulering.ws.deserializeSoap
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.LocalDate
import javax.net.ssl.SSLException

class SimuleringService(
    private val client: Soap,
    private val xmlMapper: XmlMapper = XmlMapper().apply {
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    },
) {
    private val SIMULER =
        "http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt/simulerFpService/"
    private val BEREGNING = "simulerBeregningRequest"
    private val OPPDRAG = "sendInnOppdragRequest"

    suspend fun simuler(request: SimuleringRequestBody): Simulering? {
        val request = SimuleringRequestBuilder(request).build()
        val xml = xml(request.request)

        val response = client.call(
            action = SIMULER + BEREGNING,
            body = xml,
        )

        val json = json(response)
        val simulering = simulering(json)
        return simulering
    }

    private fun json(xml: String): JsonNode {
        return runCatching {
            xmlMapper.deserializeSoap<JsonNode>(xml)
        }.onFailure {
            secureLog.error("Kunne ikke deserialisere simulering", it)
        }.getOrThrow()
    }

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

    private fun simuleringFeilet(e: SimulerBeregningFeilUnderBehandling) {
        with(e.faultInfo.errorMessage) {
            when {
                contains("Personen finnes ikke") -> throw PersonFinnesIkkeException(this)
                contains("ugyldig") -> throw RequestErUgyldigException(this)
                else -> throw e
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
                             xmlns:ns3="http://nav.no/system/os/entiteter/oppdragSkjema">
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