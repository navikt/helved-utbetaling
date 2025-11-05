package utsjekk.routes

import TestRuntime
import fakes.Azp
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import utsjekk.simulering.SimuleringSubscriptions

class SimuleringV3RouteTest {

    @Test
    fun `simuler for dagpenger`() =
        runTest {
            val key = UUID.randomUUID().toString()

            val a = async {
                httpClient.post("/api/simulering/v3") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.AZURE_TOKEN_GENERATOR))
                    header("Transaction-ID", key)
                    header("fagsystem", "DAGPENGER")
                    setBody(
                        DpUtbetaling(
                            dryrun = true,
                            behandlingId = "1234",
                            sakId = "sakId",
                            ident = "12345678910",
                            vedtakstidspunktet = LocalDateTime.now(),
                            utbetalinger = listOf(
                                DpUtbetalingsdag(
                                    meldeperiode = "18-19 aug",
                                    dato = LocalDate.of(2025, 8, 18),
                                    sats = 573u,
                                    utbetaltBeløp = 573u,
                                    utbetalingstype = Utbetalingstype.Dagpenger
                                ),
                                DpUtbetalingsdag(
                                    meldeperiode = "18-19 aug",
                                    dato = LocalDate.of(2025, 8, 19),
                                    sats = 999u,
                                    utbetaltBeløp = 999u,
                                    utbetalingstype = Utbetalingstype.Dagpenger
                                )
                            ),
                        )
                    )
                }
            }

            val sim = Simulering(
                perioder = listOf(
                    Simuleringsperiode(
                        fom = LocalDate.of(2025, 8, 18),
                        tom = LocalDate.of(2025, 8, 19),
                        utbetalinger = listOf(
                            SimulertUtbetaling(
                                fagsystem = Fagsystem.DAGPENGER,
                                sakId = "sakId",
                                utbetalesTil = "12345678910",
                                stønadstype = StønadTypeDagpenger.DAGPENGER,
                                tidligereUtbetalt = 1572,
                                nyttBeløp = 1572,
                            )
                        )
                    )
                )
            )

            TestRuntime.topics.dryrunDp.produce(key) {
                sim
            }

            val res = a.await()

            assertEquals(HttpStatusCode.OK, res.status)
            assertEquals(sim, res.body<Simulering>())
        }

    @Test
    @Disabled
    fun `simuler for dagpenger feiler med status`() =
        runTest {
            val key = UUID.randomUUID().toString()

            val a = async {
                httpClient.post("/api/simulering/v3") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.AZURE_TOKEN_GENERATOR))
                    header("Transaction-ID", key)
                    header("fagsystem", "DAGPENGER")
                    setBody(
                        DpUtbetaling(
                            dryrun = true,
                            behandlingId = "1234",
                            sakId = "sakId",
                            ident = "12345678910",
                            vedtakstidspunktet = LocalDateTime.now(),
                            utbetalinger = listOf(
                                DpUtbetalingsdag(
                                    meldeperiode = "18-19 aug",
                                    dato = LocalDate.of(2025, 8, 18),
                                    sats = 573u,
                                    utbetaltBeløp = 573u,
                                    utbetalingstype = Utbetalingstype.Dagpenger
                                ),
                                DpUtbetalingsdag(
                                    meldeperiode = "18-19 aug",
                                    dato = LocalDate.of(2025, 8, 19),
                                    sats = 999u,
                                    utbetaltBeløp = 999u,
                                    utbetalingstype = Utbetalingstype.Dagpenger
                                )
                            ),
                        )
                    )
                }
            }

            val status = StatusReply.err(ApiError(400, "bad bad bad")) 

            TestRuntime.topics.status.produce(key) {
                status
            }

            val res = a.await()

            assertEquals(HttpStatusCode.BadRequest, res.status)
            assertEquals(status, res.body<StatusReply>())
        }

    @Test
    fun `simuler for tilleggsstønader`() = runTest {
        val transactionId = UUID.randomUUID().toString()

        val a = async {
            httpClient.post("/api/simulering/v3") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.AZURE_TOKEN_GENERATOR))
                header("Transaction-ID", transactionId)
                header("fagsystem", "TILLEGGSSTØNADER")
                setBody(
                    listOf(
                        TsUtbetaling(
                            dryrun = true,
                            id = UUID.randomUUID(),
                            sakId = "sakId",
                            behandlingId = "1234",
                            personident = "12345678910",
                            stønad = StønadTypeTilleggsstønader.DAGLIG_REISE_AAP,
                            vedtakstidspunkt = LocalDateTime.now(),
                            periodetype = Periodetype.EN_GANG,
                            perioder = listOf(
                                TsPeriode(
                                    fom = LocalDate.of(2025, 10, 1),
                                    tom = LocalDate.of(2025, 10, 31),
                                    beløp = 573u,
                                ),
                            ),
                        )
                    )
                )
            }
        }

        val sim = models.v1.Simulering(
            oppsummeringer = listOf(
                models.v1.OppsummeringForPeriode(
                    fom = LocalDate.of(2025, 10, 1),
                    tom = LocalDate.of(2025, 10, 31),
                    tidligereUtbetalt = 573,
                    nyUtbetaling = 573,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0,
                )
            ),
            detaljer = models.v1.SimuleringDetaljer(
                gjelderId = "12345678910",
                datoBeregnet = LocalDate.now(),
                totalBeløp = 573,
                perioder = listOf(
                    models.v1.Periode(
                        fom = LocalDate.of(2025, 10, 1),
                        tom = LocalDate.of(2025, 10, 31),
                        posteringer = listOf(
                            models.v1.Postering(
                                fagområde = models.v1.Fagområde.TILLSTDR,
                                sakId = SakId("sakId"),
                                fom = LocalDate.of(2025, 10, 1),
                                tom = LocalDate.of(2025, 10, 31),
                                beløp = 573,
                                type = models.v1.PosteringType.YTELSE,
                                klassekode = "TSDRASISP3-OP",
                            )
                        )
                    )
                ),
            )
        )

        TestRuntime.topics.dryrunTs.produce(transactionId) {
            sim
        }

        val res = a.await()

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(sim, res.body<models.v1.Simulering>())
    }
}


