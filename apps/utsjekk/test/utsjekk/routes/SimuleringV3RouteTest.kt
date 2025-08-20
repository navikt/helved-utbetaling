package utsjekk.routes

import fakes.Azp
import httpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import models.DpUtbetaling
import models.DpUtbetalingsdag
import models.Fagsystem
import models.Rettighetstype
import models.Simulering
import models.Simuleringsperiode
import models.SimulertUtbetaling
import models.StønadTypeDagpenger
import models.Utbetalingstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.simulering.SimuleringSubscriptions

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
}

