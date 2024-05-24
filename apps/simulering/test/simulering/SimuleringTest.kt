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
import no.nav.utsjekk.kontrakter.felles.Personident
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import simulering.dto.*
import java.time.LocalDate
import kotlin.test.assertEquals

class SimuleringTest {

    @Test
    fun `svarer med 200 OK og simuleringsresultat`() {
        TestRuntime().use { runtime ->
            testApplication {
                application {
                    app(config = runtime.config)
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
    }
//
//    @Test
//    fun `simulering request xml er parset riktig`() {
//        TestRuntime().use { runtime ->
//            testApplication {
//                application {
//                    app(config = runtime.config)
//                }
//
//                val http = createClient {
//                    install(ContentNegotiation) {
//                        jackson {
//                            registerModule(JavaTimeModule())
//                            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
//                            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
//                        }
//                    }
//                }
//
//                http.post("/simulering") {
//                    contentType(ContentType.Application.Json)
//                    setBody(enSimuleringRequestBody())
//                }
//
//                val actual = runtime.receivedSoapRequests.single()
//
//                assertEquals(expected, actual)
//            }
//        }
//    }

    @Language("xml")
    private val expected: String = """
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt/simulerFpService/simulerBeregning</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:f059f280-3336-443b-b86a-0b36a81252b0</MessageID>
        <To xmlns="http://www.w3.org/2005/08/addressing">https://cics-q1.adeo.no/oppdrag/simulerFpServiceWSBinding</To>
        <ReplyTo xmlns="http://www.w3.org/2005/08/addressing">
            <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>
        </ReplyTo>
        <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" soap:mustUnderstand="1">
            hemmelig.gandalf.token

        </wsse:Security>
    </soap:Header>
    <soap:Body>
<ns3:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/entiteter/oppdragSkjema"
                             xmlns:ns3="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
                             xmlns:ns4="http://nav.no/system/os/entiteter/beregningSkjema">
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
""".trimIndent()

    @Test
    fun `svarer med 400 Bad Request ved feil på request body`() {
        TestRuntime().use { runtime ->
            testApplication {
                application {
                    app(config = runtime.config)
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

                runtime.soapResponse = Resource.read("/soap-fault.xml")
                    .replace("\$errorCode", "lol dummy 123")
                    .replace("\$errorMessage", "Fødselsnummeret er ugyldig")

                val res = http.post("/simulering") {
                    contentType(ContentType.Application.Json)
                    setBody(enSimuleringRequestBody())
                }

                assertEquals(HttpStatusCode.BadRequest, res.status)
            }
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
                                typeSats = "MND",
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
                                typeSats = "MND",
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


private fun enSimuleringRequestBody(): SimuleringApiDto {
    return SimuleringApiDto(
        fagområde = "TILLST",
        fagsystemId = "200000233",
        personident = Personident("22479409483"),
        mottaker = Personident("22479409483"),
        endringskode = Endringskode.NY,
        saksbehandler = "Z994230",
        utbetalingsfrekvens = Utbetalingsfrekvens.MÅNEDLIG,
        utbetalingslinjer =
        listOf(
            Utbetalingslinje(
                delytelseId = "200000233#0",
                endringskode = Endringskode.NY,
                klassekode = "TSTBASISP4-OP",
                fom = LocalDate.of(2024, 5, 1),
                tom = LocalDate.of(2024, 5, 1),
                sats = 700,
                grad = Grad(GradType.UFOR, null),
                refDelytelseId = null,
                refFagsystemId = null,
                datoStatusFom = null,
                statuskode = null,
                satstype = Satstype.DAG,
                utbetalesTil = "22479409483",
            ),
        ),
    )
}
