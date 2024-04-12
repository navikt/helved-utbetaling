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
import no.nav.utsjekk.kontrakter.felles.Personident
import org.junit.jupiter.api.Test
import simulering.dto.*
import simulering.ws.SoapConfig
import simulering.ws.StsConfig
import java.net.URI
import java.time.LocalDate
import kotlin.test.assertEquals


class SimuleringTest {

    private val config = Config(
        simulering = SoapConfig(
            host = "http://localhost:8083".let(::URI).toURL(),
            sts = StsConfig(
                host = "http://localhost:8084".let(::URI).toURL(),
                user = "awesome",
                pass = "såpe"
            )
        )
    )

    @Test
    fun `svarer med 200 OK og simuleringsresultat`() {
//        val request = enSimuleringRequestBody()
//        val request = SimuleringRequestBuilder(requestBody).build()

        testApplication {
            application {
                app(
                    config = config,
                    sts = FakeSts(),
                    soap = FakeSoap(),
                )
            }

            val http = createClient {
                install(ContentNegotiation) {
                    jackson {
                        registerModule(JavaTimeModule())
                        JavaTimeModule()
                        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    }
                }
            }

            val res = http.post("/simulering") {
                contentType(ContentType.Application.Json)
                setBody(enSimuleringRequestBody())
            }

            val expected = reponseXmlFagsak10001Efog()

            assertEquals(HttpStatusCode.OK, res.status)
            assertEquals(expected, res.body())
        }
    }

    private fun reponseXmlFagsak10001Efog() = Simulering(
        gjelderId = "12345678910",
        gjelderNavn = "MYGG VAKKER",
        datoBeregnet = LocalDate.parse("2022-04-05"),
        totalBelop = 1225,
        perioder = listOf(
            SimulertPeriode(
                fom = LocalDate.parse("2021-05-01"),
                tom = LocalDate.parse("2021-05-31"),
                utbetalinger = listOf(
                    Utbetaling(
                        fagSystemId = "10001",
                        utbetalesTilId = "12345678910",
                        utbetalesTilNavn = "MYGG VAKKER",
                        forfall = LocalDate.parse("2022-04-05"),
                        feilkonto = false,
                        detaljer = listOf(
                            Detaljer(
                                faktiskFom = LocalDate.parse("2021-05-01"),
                                faktiskTom = LocalDate.parse("2021-05-31"),
                                konto = "3060000",
                                belop = 12570,
                                tilbakeforing = false,
                                sats = 12570.0,
                                typeSats = "MND",
                                antallSats = 1,
                                uforegrad = 63,
                                utbetalingsType = "YTEL",
                                klassekode = "EFOG",
                                klassekodeBeskrivelse = "Enslig forsørger Overgangsstønad",
                                refunderesOrgNr = "",
                            ),
                            Detaljer(
                                faktiskFom = LocalDate.parse("2021-05-01"),
                                faktiskTom = LocalDate.parse("2021-05-31"),
                                konto = "3060000",
                                belop = -12570,
                                tilbakeforing = true,
                                sats = 0.0,
                                typeSats = "",
                                antallSats = 0,
                                uforegrad = 63,
                                utbetalingsType = "YTEL",
                                klassekode = "EFOG",
                                klassekodeBeskrivelse = "Enslig forsørger Overgangsstønad",
                                refunderesOrgNr = "",
                            )
                        ),
                    )
                )
            ),
            SimulertPeriode(
                fom = LocalDate.parse("2021-06-01"),
                tom = LocalDate.parse("2021-06-30"),
                utbetalinger = listOf(
                    Utbetaling(
                        fagSystemId = "200000476",
                        utbetalesTilId = "12345678910",
                        utbetalesTilNavn = "MYGG VAKKER",
                        forfall = LocalDate.parse("2022-04-05"),
                        feilkonto = false,
                        detaljer = listOf(
                            Detaljer(
                                faktiskFom = LocalDate.parse("2021-06-01"),
                                faktiskTom = LocalDate.parse("2021-06-30"),
                                konto = "3060000",
                                belop = 12570,
                                tilbakeforing = false,
                                sats = 12570.0,
                                typeSats = "MND",
                                antallSats = 1,
                                uforegrad = 63,
                                utbetalingsType = "YTEL",
                                klassekode = "EFOG",
                                klassekodeBeskrivelse = "Enslig forsørger Overgangsstønad",
                                refunderesOrgNr = "",
                            ),
                            Detaljer(
                                faktiskFom = LocalDate.parse("2021-06-01"),
                                faktiskTom = LocalDate.parse("2021-06-30"),
                                konto = "3060000",
                                belop = -12570,
                                tilbakeforing = true,
                                sats = 0.0,
                                typeSats = "",
                                antallSats = 0,
                                uforegrad = 63,
                                utbetalingsType = "YTEL",
                                klassekode = "EFOG",
                                klassekodeBeskrivelse = "Enslig forsørger Overgangsstønad",
                                refunderesOrgNr = "",
                            )
                        ),
                    )
                )
            ),
        ),
    )
}

//class SimulerFpFake private constructor(
//    private val beregningResponse: SimulerBeregningResponse? = null,
//    private val oppdragResponse: SendInnOppdragResponse? = null,
//) : SimulerFpService {
//    companion object {
//        fun fakeWith(res: SimulerBeregningResponse) = SimulerFpFake(beregningResponse = res)
//        fun fakeWith(res: SendInnOppdragResponse) = SimulerFpFake(oppdragResponse = res)
//    }
//
//    override fun simulerBeregning(req: SimulerBeregningRequest): SimulerBeregningResponse {
//        return beregningResponse ?: error("fake initiated without SimulerBeregningResponse")
//    }
//
//    override fun sendInnOppdrag(req: SendInnOppdragRequest): SendInnOppdragResponse {
//        return oppdragResponse ?: error("fake initiated without SendInnOppdragResponse")
//    }
//}

private fun enSimuleringRequestBody(): SimuleringRequestBody {
    return SimuleringRequestBody(
        fagområde = "TEST",
        fagsystemId = "FAGSYSTEM",
        personident = Personident("15507600333"),
        mottaker = Personident("15507600333"),
        endringskode = Endringskode.NY,
        saksbehandler = "TEST",
        utbetalingsfrekvens = Utbetalingsfrekvens.UKENTLIG,
        utbetalingslinjer =
        listOf(
            Utbetalingslinje(
                delytelseId = "",
                endringskode = Endringskode.NY,
                klassekode = "",
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 30),
                sats = 1000,
                grad = 100,
                refDelytelseId = null,
                refFagsystemId = null,
                datoStatusFom = null,
                statuskode = null,
                satstype = Satstype.MÅNED,
            ),
        ),
    )
}

//private fun enSimulerBeregningResponse(request: SimulerBeregningRequest) =
//    SimulerBeregningResponse().apply {
//        response =
//            no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse().apply {
//                simulering =
//                    Beregning().apply {
//                        gjelderId = request.request.oppdrag.oppdragGjelderId
//                        gjelderNavn = "Navn Navnesen"
//                        datoBeregnet = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
//                        kodeFaggruppe = "KORTTID"
//                        belop = BigDecimal("1234")
//                        beregningsPeriode.add(
//                            BeregningsPeriode().apply {
//                                periodeFom = request.request.simuleringsPeriode.datoSimulerFom
//                                periodeTom = request.request.simuleringsPeriode.datoSimulerTom
//                                beregningStoppnivaa.add(
//                                    BeregningStoppnivaa().apply {
//                                        kodeFagomraade = "et fagrområde"
//                                        stoppNivaaId = BigInteger("1")
//                                        behandlendeEnhet = "8052"
//                                        oppdragsId = 1234
//                                        fagsystemId = "en fagsystem-ID"
//                                        kid = "12345"
//                                        utbetalesTilId = request.request.oppdrag.oppdragGjelderId
//                                        utbetalesTilNavn = "En Arbeidsgiver AS"
//                                        bilagsType = "U"
//                                        forfall = "2023-12-28"
//                                        isFeilkonto = false
//                                        beregningStoppnivaaDetaljer.add(
//                                            BeregningStoppnivaaDetaljer().apply {
//                                                faktiskFom = request.request.simuleringsPeriode.datoSimulerFom
//                                                faktiskTom = request.request.simuleringsPeriode.datoSimulerTom
//                                                kontoStreng = "1235432"
//                                                behandlingskode = "2"
//                                                belop = BigDecimal("1000")
//                                                trekkVedtakId = 0
//                                                stonadId = "1234"
//                                                korrigering = ""
//                                                isTilbakeforing = false
//                                                linjeId = BigInteger("21423")
//                                                sats = BigDecimal("1000")
//                                                typeSats = "MND"
//                                                antallSats = BigDecimal("21")
//                                                saksbehId = "5323"
//                                                uforeGrad = BigInteger("100")
//                                                kravhaverId = ""
//                                                delytelseId = "5323"
//                                                bostedsenhet = "4643"
//                                                skykldnerId = ""
//                                                klassekode = ""
//                                                klasseKodeBeskrivelse = "en kladdekodebeskrivelse"
//                                                typeKlasse = "YTEL"
//                                                typeKlasseBeskrivelse = "en typeklassebeskrivelse"
//                                                refunderesOrgNr = ""
//                                            },
//                                        )
//                                    },
//                                )
//                            },
//                        )
//                    }
//                infomelding = null
//            }
//    }