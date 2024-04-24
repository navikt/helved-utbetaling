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
import libs.ws.SoapConfig
import libs.ws.StsConfig
import no.nav.utsjekk.kontrakter.felles.Personident
import org.junit.jupiter.api.Test
import simulering.dto.*
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
        testApplication {
            application {
                app(
                    config = config,
                    sts = FakeSts(),
                    soap = FakeSoap.with(resources("/simuler-body-response.xml")),
                )
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

            val expected = reponseXmlFagsak10001Efog()

            assertEquals(HttpStatusCode.OK, res.status)
            assertEquals(expected, res.body())
        }
    }

    @Test
    fun `svarer med 400 Bad Request ved feil på request body`() {
        testApplication {
            application {
                app(
                    config = config,
                    sts = FakeSts(),
                    soap = FakeSoap.with(resources("/soap-fault.xml")) {
                        it.replace("\$errorCode", "lol dummy 123")
                            .replace("\$errorMessage", "Fødselsnummeret er ugyldig")
                    },
                )
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

            assertEquals(HttpStatusCode.BadRequest, res.status)
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
