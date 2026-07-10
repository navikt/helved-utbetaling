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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SimuleringV3RouteTest {

    @BeforeEach
    fun setup() {
        TestRuntime.simulering.reset()
    }

    @Test
    fun `simuler for dagpenger via v3 proxy`() = runTest {
        val key = UUID.randomUUID().toString()
        val sim = v2.Simulering(
            perioder = listOf(
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2025, 8, 18),
                    tom = LocalDate.of(2025, 8, 19),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.DAGPENGER,
                            sakId = "sakId",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeDagpenger.DAGPENGER,
                            tidligereUtbetalt = 1572,
                            nyttBeløp = 1572,
                            posteringer = listOf(),
                        )
                    )
                )
            )
        )

        val json = libs.kotlinx.KotlinxJson.encodeToString(Simulering.serializer(), sim)
        TestRuntime.simulering.respondDryrunWith(json, HttpStatusCode.OK)

        val res = httpClient.post("/api/simulering/v3") {
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
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(sim, res.body<Simulering>())
    }

    @Test
    fun `dryrun dagpenger via ny route`() = runTest {
        val key = UUID.randomUUID().toString()
        val sim = v2.Simulering(
            perioder = listOf(
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2025, 8, 18),
                    tom = LocalDate.of(2025, 8, 18),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.DAGPENGER,
                            sakId = "sakId",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeDagpenger.DAGPENGER,
                            tidligereUtbetalt = 573,
                            nyttBeløp = 573,
                            posteringer = listOf(),
                        )
                    )
                )
            )
        )

        val json = libs.kotlinx.KotlinxJson.encodeToString(Simulering.serializer(), sim)
        TestRuntime.simulering.respondDryrunWith(json, HttpStatusCode.OK)

        val res = httpClient.post("/api/dryrun/dagpenger") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.DAGPENGER))
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
                            utbetalingstype = Utbetalingstype.Dagpenger
                        ),
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(sim, res.body<Simulering>())
    }

    @Test
    fun `dryrun tilleggsstonader via ny route`() = runTest {
        val transactionId = UUID.randomUUID().toString()

        val sim = v1.Simulering(
            oppsummeringer = listOf(
                v1.OppsummeringForPeriode(
                    fom = LocalDate.of(2025, 10, 1),
                    tom = LocalDate.of(2025, 10, 31),
                    tidligereUtbetalt = 573,
                    nyUtbetaling = 573,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0,
                ),
            ),
            detaljer = v1.SimuleringDetaljer(
                gjelderId = "12345678910",
                datoBeregnet = LocalDate.now(),
                totalBeløp = 573,
                perioder = listOf(
                    v1.Periode(
                        fom = LocalDate.of(2025, 10, 1),
                        tom = LocalDate.of(2025, 10, 31),
                        posteringer = listOf(
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLSTDR,
                                sakId = SakId("sakId"),
                                fom = LocalDate.of(2025, 10, 1),
                                tom = LocalDate.of(2025, 10, 31),
                                beløp = 573,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSDRASISP3-OP",
                            )
                        )
                    ),
                ),
            )
        )

        val json = libs.kotlinx.KotlinxJson.encodeToString(Simulering.serializer(), sim)
        TestRuntime.simulering.respondDryrunWith(json, HttpStatusCode.OK)

        val res = httpClient.post("/api/dryrun/tilleggsstonader") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILLEGGSSTØNADER))
            header("Transaction-ID", transactionId)
            setBody(
                TsDto(
                    dryrun = true,
                    sakId = "sakId",
                    behandlingId = "1234",
                    personident = "12345678910",
                    vedtakstidspunkt = LocalDateTime.now(),
                    periodetype = Periodetype.EN_GANG,
                    saksbehandler = null,
                    beslutter = null,
                    utbetalinger = listOf(
                        TsUtbetaling(
                            id = UUID.randomUUID(),
                            stønad = StønadTypeTilleggsstønader.DAGLIG_REISE_AAP,
                            brukFagområdeTillst = false,
                            perioder = listOf(
                                TsPeriode(
                                    fom = LocalDate.of(2025, 10, 1),
                                    tom = LocalDate.of(2025, 10, 31),
                                    beløp = 573u,
                                ),
                            ),
                        ),
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(sim, res.body<v1.Simulering>())
    }

    @Test
    fun `dryrun proxy returns timeout from simulering`() = runTest {
        TestRuntime.simulering.respondDryrunWith("", HttpStatusCode.RequestTimeout)

        val res = httpClient.post("/api/dryrun/dagpenger") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.DAGPENGER))
            header("Transaction-ID", UUID.randomUUID().toString())
            setBody(
                DpUtbetaling(
                    dryrun = true,
                    behandlingId = "1234",
                    sakId = "sakId",
                    ident = "12345678910",
                    vedtakstidspunktet = LocalDateTime.now(),
                    utbetalinger = listOf(),
                )
            )
        }

        assertEquals(HttpStatusCode.RequestTimeout, res.status)
    }
}
