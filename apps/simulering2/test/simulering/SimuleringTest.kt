package simulering

import libs.utils.Resource
import models.ApiError
import models.kontrakter.Personident
import org.http4k.core.*
import org.http4k.lens.BiDiBodyLens
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import simulering.models.rest.rest
import simulering.models.soap.soap
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimuleringTest {

    private val requestLens: BiDiBodyLens<rest.SimuleringRequest> = KotlinxJson.autoBody<rest.SimuleringRequest>().toLens()
    private val responseLens: BiDiBodyLens<rest.SimuleringResponse> = KotlinxJson.autoBody<rest.SimuleringResponse>().toLens()

    @Test
    fun `svarer med 200 OK og simuleringsresultat`() {
        TestRuntime().use { runtime ->
            val app = simulering(config = runtime.config)

            val response = app(
                Request(Method.POST, "/simulering")
                    .header("Content-Type", "application/json")
                    .with(requestLens of enSimuleringRequestBody())
            )

            val expected = responseXmlFagsak10001Efog()

            assertEquals(Status.OK, response.status)
            assertEquals(expected, responseLens(response))
        }
    }

    @Test
    fun `aksepterer personident som objekt med verdi-felt`() {
        TestRuntime().use { runtime ->
            val app = simulering(config = runtime.config)

            val json = """{"fagområde":"TILLST","sakId":"200000233","personident":{"verdi":"22479409483"},"erFørsteUtbetalingPåSak":true,"saksbehandler":"Z994230","utbetalingsperioder":[{"periodeId":"0","forrigePeriodeId":null,"erEndringPåEksisterendePeriode":false,"klassekode":"TSTBASISP4-OP","fom":"2024-05-01","tom":"2024-05-01","sats":700,"satstype":"DAG","opphør":null,"utbetalesTil":"22479409483"}]}"""

            val response = app(
                Request(Method.POST, "/simulering")
                    .header("Content-Type", "application/json")
                    .body(json)
            )

            assertEquals(Status.OK, response.status)
        }
    }

    @Test
    fun `can resolve simulering response`() {
        val actual = TestRuntime().use { runtime ->
            val simulering = SimuleringService(runtime, runtime)
            simulering.json(Resource.read("/sim-res.xml"))
        }

        val expected = simuleringResponse().simulerBeregningResponse.response!!.simulering

        assertEquals(expected, actual)
    }

    @Test
    fun `can resolve empty simulering response`() {
        val actual = TestRuntime().use { runtime ->
            val simulering = SimuleringService(runtime, runtime)
            simulering.json(Resource.read("/simuler-tom.xml"))
        }

        assertNull(actual)
    }

    @Test
    fun `kan deserialisere trekkpostering`() {
        assertDoesNotThrow {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime, runtime)
                simulering.json(Resource.read("/sim-trekk.xml"))
            }
        }
    }

    @Test
    fun `kan deserialisere motposteringer`() {
        assertDoesNotThrow {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime, runtime)
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

        val actual = assertThrows<ApiError> {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime, runtime)
                simulering.json(fault)
            }
        }
        assertEquals(409, actual.statusCode)
        assertEquals("Utbetaling med SakId/BehandlingId finnes fra før", actual.message)
    }

    @Test
    fun `resolver soap-fault person ikke funnet i PDL`() {
        val fault = enFault(errorMessage = "##Navn på person ikke funnet i PDL")

        val actual = assertThrows<ApiError> {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime, runtime)
                simulering.json(fault)
            }
        }
        assertEquals(404, actual.statusCode)
        assertEquals("Navn på person ikke funnet i PDL", actual.message)
    }

    @Test
    fun `osap fault BB50024F is default soapException`() {
        val fault = enFault(errorMessage = "KODE-ENDRING-LINJE ulik NY, Ref-feltene utfylt")

        assertThrows<SoapException> {
            TestRuntime().use { runtime ->
                val simulering = SimuleringService(runtime, runtime)
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
