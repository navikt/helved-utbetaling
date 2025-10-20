package simulering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import libs.utils.Resource
import libs.ws.SoapException
import models.kontrakter.felles.Personident
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import simulering.models.rest.rest
import simulering.models.soap.soap
import java.time.LocalDate
import kotlin.test.assertEquals

class SimuleringTest {

    @Test
    fun `svarer med 200 OK og simuleringsresultat`() {
        TestRuntime().use { runtime ->
            testApplication {
                application {
                    simulering(config = runtime.config)
                }

                val http = createClient {
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        }
                    }
                }

                val res = http.post("/simulering") {
                    contentType(ContentType.Application.Json)
                    setBody(enSimuleringRequestBody())
                }

                val expected = responseXmlFagsak10001Efog()

                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals(expected, res.body())
            }
        }
    }

    @Test
    fun `can resolve simulering response`() {
        val actual = TestRuntime().use { runtime ->
            val simulering = SimuleringService(runtime.config)
            simulering.json(Resource.read("/sim-res.xml"))
        }

        val expected = simuleringResponse().simulerBeregningResponse.response!!.simulering

        assertEquals(expected, actual)
    }

    @Test
    fun `can resolve empty simulering response`() {
        val actual = TestRuntime().use { runtime ->
            val simulering = SimuleringService(runtime.config)
            simulering.json(Resource.read("/simuler-tom.xml"))
        }

        assertNull(actual)
    }

    @Test
    fun `kan deserialisere trekkpostering`() {
        assertDoesNotThrow {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime.config)
                simulering.json(Resource.read("/sim-trekk.xml"))
            }
        }
    }

    @Test
    fun `kan deserialisere motposteringer`() {
        assertDoesNotThrow {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime.config)
                simulering.json(Resource.read("/simuler-endring-response.xml"))
            }
        }
    }

    private fun simuleringResponse(): soap.SimuleringResponse {
        return soap.SimuleringResponse(
            simulerBeregningResponse = soap.SimulerBeregningResponse(
                response = soap.Response(
                    simulering = soap.Beregning(
                        gjelderId = "22479409483",
                        datoBeregnet = LocalDate.of(2024, 5, 24),
                        belop = 700.0,
                        beregningsPeriode = listOf(
                            soap.Periode(
                                periodeFom = LocalDate.of(2024, 5, 1),
                                periodeTom = LocalDate.of(2024, 5, 1),
                                beregningStoppnivaa = listOf(
                                    soap.Stoppnivå(
                                        kodeFagomraade = "TILLST",
                                        fagsystemId = "200000238",
                                        utbetalesTilId = "22479409483",
                                        forfall = LocalDate.of(2024, 5, 24),
                                        feilkonto = false,
                                        beregningStoppnivaaDetaljer = listOf(
                                            soap.Detalj(
                                                faktiskFom = LocalDate.of(2024, 5, 1),
                                                faktiskTom = LocalDate.of(2024, 5, 1),
                                                belop = 700.0,
                                                trekkVedtakId = 0,
                                                sats = 700.0,
                                                typeSats = soap.SatsType.DAG,
                                                klassekode = "TSTBASISP4-OP",
                                                typeKlasse = "YTEL",
                                                refunderesOrgNr = "",
                                            )
                                        ),
                                    )
                                ),
                            )
                        )
                    ),
                    infomelding = null,
                )
            )
        )
    }

    @Test
    fun `can resolve soap fault`() {
        val fault = enFault(errorMessage = "Oppdraget finnes fra før")

        val actual = assertThrows<FinnesFraFør> {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime.config)
                simulering.json(fault)
            }
        }
        assertEquals("Utbetaling med SakId/BehandlingId finnes fra før", actual.message)
    }

    @Test
    fun `resolver soap-fault person ikke funnet i PDL`() {
        val fault = enFault(errorMessage = "##Navn på person ikke funnet i PDL")

        val actual = assertThrows<IkkeFunnet> {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime.config)
                simulering.json(fault)
            }
        }
        assertEquals("Navn på person ikke funnet i PDL", actual.message)
    }

    @Test
    fun `osap fault BB50024F is default soapException`() {
        val fault = enFault(errorMessage = "KODE-ENDRING-LINJE ulik NY, Ref-feltene utfylt")

        assertThrows<SoapException> {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime.config)
                simulering.json(fault)
            }
        }
    }

    private fun enFault(faultString: String = "simulerBeregningFeilUnderBehandling", errorMessage: String) = """
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            <SOAP-ENV:Body>
                <SOAP-ENV:Fault xmlns="">
                    <faultcode>SOAP-ENV:Client</faultcode>
                    <faultstring>$faultString</faultstring>
                    <detail>
                        <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/oppdragService">
                            <errorMessage>$errorMessage</errorMessage>
                            <errorSource>K231BB50 section: CA10-KON</errorSource>
                            <rootCause>Kode BB50024F - SQL - MQ</rootCause>
                            <dateTimeStamp>2024-06-14T13:57:08</dateTimeStamp>
                        </sf:simulerBeregningFeilUnderBehandling>
                    </detail>
                </SOAP-ENV:Fault>
            </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
    """.trimIndent()

    @Language("xml")
    private val expected: String = """
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">
            http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt/simulerFpService/simulerBeregning
        </Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:f059f280-3336-443b-b86a-0b36a81252b0
        </MessageID>
        <To xmlns="http://www.w3.org/2005/08/addressing">https://cics-q1.adeo.no/oppdrag/simulerFpServiceWSBinding</To>
        <ReplyTo xmlns="http://www.w3.org/2005/08/addressing">
            <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>
        </ReplyTo>
        <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" soap:mustUnderstand="1">
            hemmelig.gandalf.token
        </wsse:Security>
    </soap:Header>
    <soap:Body>
        <ns3:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/entiteter/oppdragSkjema" xmlns:ns3="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
            <request>
                <oppdrag>
                    <kodeEndring>NY</kodeEndring>
                    <kodeFagomraade>TILLST</kodeFagomraade>
                    <fagsystemId>200000233</fagsystemId>
                    <utbetFrekvens>MND</utbetFrekvens>
                    <oppdragGjelderId>22479409483</oppdragGjelderId>
                    <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
                    <saksbehId>Z994230</saksbehId>
                    <ns2:enhet>
                        <typeEnhet>BOS</typeEnhet>
                        <enhet>8020</enhet>
                        <datoEnhetFom>1970-01-01</datoEnhetFom>
                    </ns2:enhet>
                    <oppdragslinje>
                        <kodeEndringLinje>NY</kodeEndringLinje>
                        <delytelseId>200000233#0</delytelseId>
                        <kodeKlassifik>TSTBASISP4-OP</kodeKlassifik>
                        <datoKodeKlassifikFom>2024-05-01</kodeKlassifik>
                        <datoVedtakFom>2024-05-01</datoVedtakFom>
                        <datoVedtakTom>2024-05-01</datoVedtakTom>
                        <sats>700</sats>
                        <fradragTillegg>T</fradragTillegg>
                        <typeSats>DAG</typeSats>
                        <brukKjoreplan>N</brukKjoreplan>
                        <saksbehId>Z994230</saksbehId>
                        <utbetalesTilId>22479409483</utbetalesTilId>
                        <ns2:grad>
                            <typeGrad>UFOR</typeGrad>
                        </ns2:grad>
                        <ns2:attestant>
                            <attestantId>Z994230</attestantId>
                        </ns2:attestant>
                    </oppdragslinje>
                </oppdrag>
                <simuleringsPeriode>
                    <datoSimulerFom>2024-05-01</datoSimulerFom>
                    <datoSimulerTom>2024-05-01</datoSimulerTom>
                </simuleringsPeriode>
            </request>
        </ns3:simulerBeregningRequest>
    </soap:Body>
</soap:Envelope>
""".trimIndent()

    private fun responseXmlFagsak10001Efog() = rest.SimuleringResponse(
        gjelderId = "12345678910",
        datoBeregnet = LocalDate.parse("2022-04-05"),
        totalBelop = 1225,
        perioder = listOf(
            rest.SimulertPeriode(
                fom = LocalDate.parse("2021-05-01"),
                tom = LocalDate.parse("2021-05-31"),
                utbetalinger = listOf(
                    rest.Utbetaling(
                        fagområde = "TILLST",
                        fagSystemId = "10001",
                        utbetalesTilId = "12345678910",
                        forfall = LocalDate.parse("2022-04-05"),
                        feilkonto = false,
                        detaljer = listOf(
                            rest.Postering(
                                type = "YTEL",
                                faktiskFom = LocalDate.parse("2021-05-01"),
                                faktiskTom = LocalDate.parse("2021-05-31"),
                                belop = 12570,
                                sats = 12570.0,
                                satstype = "MND",
                                klassekode = "TSTBASISP4-OP",
                                refunderesOrgNr = null,
                                trekkVedtakId = null,
                            ),
                            rest.Postering(
                                type = "YTEL",
                                faktiskFom = LocalDate.parse("2021-05-01"),
                                faktiskTom = LocalDate.parse("2021-05-31"),
                                belop = -12570,
                                sats = 0.0,
                                satstype = "MND",
                                klassekode = "TSTBASISP4-OP",
                                refunderesOrgNr = null,
                                trekkVedtakId = null,
                            )
                        ),
                    )
                )
            ),
            rest.SimulertPeriode(
                fom = LocalDate.parse("2021-06-01"),
                tom = LocalDate.parse("2021-06-30"),
                utbetalinger = listOf(
                    rest.Utbetaling(
                        fagområde = "TILLST",
                        fagSystemId = "200000476",
                        utbetalesTilId = "12345678910",
                        forfall = LocalDate.parse("2022-04-05"),
                        feilkonto = false,
                        detaljer = listOf(
                            rest.Postering(
                                type = "YTEL",
                                faktiskFom = LocalDate.parse("2021-06-01"),
                                faktiskTom = LocalDate.parse("2021-06-30"),
                                belop = 12570,
                                sats = 12570.0,
                                satstype = "MND",
                                klassekode = "TSTBASISP4-OP",
                                refunderesOrgNr = null,
                                trekkVedtakId = null,
                            ),
                            rest.Postering(
                                type = "YTEL",
                                faktiskFom = LocalDate.parse("2021-06-01"),
                                faktiskTom = LocalDate.parse("2021-06-30"),
                                belop = -12570,
                                sats = 0.0,
                                satstype = "MND",
                                klassekode = "TSTBASISP4-OP",
                                refunderesOrgNr = null,
                                trekkVedtakId = null,
                            )
                        ),
                    )
                )
            ),
        ),
    )
}

/**
 * Replaces the content between the XML tags with the given replacement.
 * @example <tag>original</tag> -> <tag>replacement</tag>
 */
fun String.replaceBetweenXmlTag(tag: String, replacement: String): String {
    return replace(
        regex = Regex("(?<=<$tag>).*(?=</$tag>)"),
        replacement = replacement
    )
}


internal fun enSimuleringRequestBody(): rest.SimuleringRequest {
    return rest.SimuleringRequest(
        fagområde = "TILLST",
        sakId = "200000233",
        personident = Personident("22479409483"),
        erFørsteUtbetalingPåSak = true,
        saksbehandler = "Z994230",
        utbetalingsperioder =
        listOf(
            rest.Utbetalingsperiode(
                periodeId = "0",
                forrigePeriodeId = null,
                erEndringPåEksisterendePeriode = false,
                klassekode = "TSTBASISP4-OP",
                fom = LocalDate.of(2024, 5, 1),
                tom = LocalDate.of(2024, 5, 1),
                sats = 700,
                satstype = rest.SatsType.DAG,
                utbetalesTil = "22479409483",
                opphør = null,
            ),
        ),
    )
}
