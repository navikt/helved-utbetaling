package utsjekk.routes

import TestRuntime
import fakes.Azp
import httpClient
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Properties
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import libs.kafka.Names
import libs.kafka.SslConfig
import libs.kafka.StreamsMock
import libs.kafka.StreamsConfig
import libs.ktor.KtorRuntime
import models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import utsjekk.Config
import utsjekk.Metrics
import utsjekk.Tables
import utsjekk.Topics
import utsjekk.createTopology
import utsjekk.utsjekk

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

        assertEquals(HttpStatusCode.RequestTimeout, res.status)
        assertEquals(
            DryrunTimeoutBody(
                reason = "timeout",
                transactionId = key,
                elapsedMs = 120_000,
                msg = "Dryrun did not complete within 120s — try again or check abetal status",
            ),
            res.body<DryrunTimeoutBody>(),
        )
    }

    @Test
    fun `simuler for tilleggsstønader`() = runTest {
        val transactionId = UUID.randomUUID().toString()
        val dto = TsDto(
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
                TsUtbetaling(
                    id = UUID.randomUUID(),
                    stønad = StønadTypeTilleggsstønader.DAGLIG_REISE_AAP,
                    brukFagområdeTillst = false,
                    perioder = listOf(
                        TsPeriode(
                            fom = LocalDate.of(2025, 11, 1),
                            tom = LocalDate.of(2025, 11, 30),
                            beløp = 574u,
                        ),
                    ),
                )
            )
        )

        val a = async {
            httpClient.post("/api/simulering/v3") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.AZURE_TOKEN_GENERATOR))
                header("Transaction-ID", transactionId)
                header("fagsystem", "TILLEGGSSTØNADER")
                setBody(dto)
            }
        }


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
                v1.OppsummeringForPeriode(
                    fom = LocalDate.of(2025, 11, 1),
                    tom = LocalDate.of(2025, 11, 30),
                    tidligereUtbetalt = 574,
                    nyUtbetaling = 574,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0,
                )
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
                    v1.Periode(
                        fom = LocalDate.of(2025, 11, 1),
                        tom = LocalDate.of(2025, 11, 30),
                        posteringer = listOf(
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLSTDR,
                                sakId = SakId("sakId"),
                                fom = LocalDate.of(2025, 11, 1),
                                tom = LocalDate.of(2025, 11, 30),
                                beløp = 574,
                                type = v1.PosteringType.YTELSE,
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

        val tsTopic = TestRuntime.kafka.getProducer(Topics.utbetalingTs)
        assertEquals(0, tsTopic.uncommitted().size)
        val actual = tsTopic.history().singleOrNull { (key, _) -> key == transactionId }?.second
        assertEquals(dto, actual)

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(sim, res.body<v1.Simulering>())
    }

    @Test
    fun `dryrun dagpenger via ny route`() = runTest {
        val key = UUID.randomUUID().toString()

        val a = async {
            httpClient.post("/api/dryrun/dagpenger") {
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
        }

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

        TestRuntime.topics.dryrunDp.produce(key) {
            sim
        }

        val res = a.await()

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(sim, res.body<Simulering>())
    }

    @Test
    fun `dryrun tilleggsstonader via ny route`() = runTest {
        val transactionId = UUID.randomUUID().toString()
        val dto = TsDto(
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

        val a = async {
            httpClient.post("/api/dryrun/tilleggsstonader") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILLEGGSSTØNADER))
                header("Transaction-ID", transactionId)
                setBody(dto)
            }
        }

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

        TestRuntime.topics.dryrunTs.produce(transactionId) {
            sim
        }

        val res = a.await()

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(sim, res.body<v1.Simulering>())
    }

    @Test
    fun `dryrun aap avviser feil klient`() = runTest {
        val res = httpClient.post("/api/dryrun/aap") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILLEGGSSTØNADER))
            header("Transaction-ID", UUID.randomUUID().toString())
            setBody(
                AapUtbetaling(
                    dryrun = true,
                    sakId = "sakId",
                    behandlingId = "1234",
                    ident = "12345678910",
                    vedtakstidspunktet = LocalDateTime.now(),
                    utbetalinger = listOf()
                )
            )
        }

        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `dryrun dagpenger timeouter med strukturert body`() = runTest {
        val transactionId = UUID.randomUUID().toString()
        val res = TimeoutRuntime.httpClient.post("/api/dryrun/dagpenger") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.DAGPENGER))
            header("Transaction-ID", transactionId)
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
                            utbetalingstype = Utbetalingstype.Dagpenger,
                        ),
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.RequestTimeout, res.status)
        assertEquals(
            DryrunTimeoutBody(
                reason = "timeout",
                transactionId = transactionId,
                elapsedMs = 1,
                msg = "Dryrun did not complete within 120s — try again or check abetal status",
            ),
            res.body<DryrunTimeoutBody>(),
        )

    }

    @Test
    fun `dryrun dagpenger mangler transaction id gir 404`() = runTest {
        val res = httpClient.post("/api/dryrun/dagpenger") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.DAGPENGER))
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
                            utbetalingstype = Utbetalingstype.Dagpenger,
                        ),
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals("Mangler header Transaction-ID", res.body<ApiError>().msg)
    }
}

private object TimeoutRuntime {
    private val kafkaMock: StreamsMock by lazy { StreamsMock() }
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val metrics = Metrics(meterRegistry)
    private val config = run {
        val workerId = java.lang.System.getProperty("org.gradle.test.worker") ?: "0"
        val stateDir = "build/kafka-streams/timeout-state-w$workerId-${java.lang.System.nanoTime()}"
        TestRuntime.config.copy(
            kafka = StreamsConfig(
                applicationId = "test-timeout-application-w$workerId-${java.util.UUID.randomUUID()}",
                brokers = TestRuntime.config.kafka.brokers,
                ssl = SslConfig("", "", ""),
                additionalProperties = Properties().apply {
                    this["state.dir"] = stateDir
                }
            )
        )
    }

    val ktor: KtorRuntime<Config> by lazy {
        Names.clear()
        KtorRuntime<Config>(
            appName = "utsjekk-timeout",
            module = {
                utsjekk(
                    config,
                    kafkaMock,
                    jdbcCtx = TestRuntime.context,
                    topology = kafkaMock.append(createTopology(TestRuntime.context, metrics)) {
                        consume(Tables.saker)
                    },
                    meterRegistry = meterRegistry,
                    metrics = metrics,
                    dryrunTimeout = 1.milliseconds,
                    startupValidation = {},
                )
            },
            onClose = {},
        )
    }

    val httpClient get() = ktor.httpClient
}
