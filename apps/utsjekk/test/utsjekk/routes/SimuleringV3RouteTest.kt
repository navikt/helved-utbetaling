package utsjekk.routes

import TestRuntime
import fakes.Azp
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.simulering.SimuleringSubscriptions
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SimuleringRouteTest {

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
                                    rettighetstype = Rettighetstype.Ordinær,
                                    utbetalingstype = Utbetalingstype.Dagpenger
                                ),
                                DpUtbetalingsdag(
                                    meldeperiode = "18-19 aug",
                                    dato = LocalDate.of(2025, 8, 19),
                                    sats = 999u,
                                    utbetaltBeløp = 999u,
                                    rettighetstype = Rettighetstype.Ordinær,
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
                                stønadstype = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                                tidligereUtbetalt = 1572,
                                nyttBeløp = 1572,
                            )
                        )
                    )
                )
            )

            launch {
                val subscription = SimuleringSubscriptions.subscriptionEvents.receive()
                assertEquals(key, subscription)

                TestRuntime.topics.dryrunDp.produce(key) {
                    sim
                }
            }

            val res = a.await()

            assertEquals(HttpStatusCode.OK, res.status)
            assertEquals(sim, res.body<Simulering>())
        }

    @Test
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
                                    rettighetstype = Rettighetstype.Ordinær,
                                    utbetalingstype = Utbetalingstype.Dagpenger
                                ),
                                DpUtbetalingsdag(
                                    meldeperiode = "18-19 aug",
                                    dato = LocalDate.of(2025, 8, 19),
                                    sats = 999u,
                                    utbetaltBeløp = 999u,
                                    rettighetstype = Rettighetstype.Ordinær,
                                    utbetalingstype = Utbetalingstype.Dagpenger
                                )
                            ),
                        )
                    )
                }
            }

            val status = StatusReply.err(ApiError(400, "bad bad bad")) 

            launch {
                val subscription = SimuleringSubscriptions.subscriptionEvents.receive()
                assertEquals(key, subscription)

                TestRuntime.topics.status.produce(key) {
                    status
                }
            }

            val res = a.await()

            assertEquals(HttpStatusCode.BadRequest, res.status)
            assertEquals(status, res.body<StatusReply>())
        }
}


