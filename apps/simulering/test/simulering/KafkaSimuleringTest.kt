package simulering

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libs.kafka.KafkaProducerFake
import libs.utils.Resource
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class KafkaSimuleringTest {
    private val runtime = TestRuntime()
    private val service = SimuleringService(runtime, runtime)

    @AfterEach
    fun cleanup() {
        runtime.close()
        libs.kafka.Names.clear()
    }

    @Test
    fun `simulering for TS produserer v1 resultat til dryrun-ts topic`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        // SOAP response with TILLST fagområde
        runtime.soapRespondWith(jaxbResponse())

        val request = simulering(fagområde = "TILLST")
        channel.trySend("test-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.TILLEGGSSTØNADER]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("test-key", key)
        assertTrue(result is v1.Simulering)
    }

    @Test
    fun `simulering for AAP produserer v2 resultat til dryrun-aap topic`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        runtime.soapRespondWith(jaxbResponse())

        val request = simulering(fagområde = "AAP")
        channel.trySend("aap-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.AAP]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("aap-key", key)
        assertTrue(result is v2.Simulering)
    }

    @Test
    fun `SOAP fault produserer Info med UGYLDIG_REQUEST`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        val fault = """
            <faultcode>soap:Server</faultcode>
            <faultstring>simulerBeregningFeilUnderBehandling</faultstring>
            <detail>
                <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                    <errorMessage>Personen finnes ikke</errorMessage>
                </sf:simulerBeregningFeilUnderBehandling>
            </detail>
        """.trimIndent()
        runtime.soapRespondWith(soapFault(fault))

        val request = simulering(fagområde = "DP")
        channel.trySend("dp-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.DAGPENGER]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("dp-key", key)
        assertTrue(result is Info)
        assertEquals(Info.Status.UGYLDIG_REQUEST, result.status)
    }

    @Test
    fun `tom SOAP-respons produserer Info OkUtenEndring for TS`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        // Empty response — simulerBeregningResponse with no inner <response>
        runtime.soapRespondWith("""
            <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"></simulerBeregningResponse>
        """.trimIndent())

        val request = simulering(fagområde = "TILLST")
        channel.trySend("ts-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.TILLEGGSSTØNADER]!!.history()
        assertEquals(1, history.size)
        val (_, result) = history.first()
        assertTrue(result is Info)
        assertEquals(Info.Status.OK_UTEN_ENDRING, result.status)
    }

    @Test
    fun `simuler sak 200001495 produserer korrekt v1 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        runtime.soapRespondWith(Resource.read("/simuler-ts-200001495.xml"))

        val request = simulering(fagområde = "TILLST")
        channel.trySend("ts-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.TILLEGGSSTØNADER]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("ts-key", key)

        val expected = v1.Simulering(
            oppsummeringer = listOf(
                v1.OppsummeringForPeriode(
                    fom = LocalDate.of(2026, 2, 4),
                    tom = LocalDate.of(2026, 2, 4),
                    tidligereUtbetalt = 970,
                    nyUtbetaling = 340,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 630,
                ),
            ),
            detaljer = v1.SimuleringDetaljer(
                gjelderId = "12345678910",
                datoBeregnet = LocalDate.of(2026, 2, 12),
                totalBeløp = 0,
                perioder = listOf(
                    v1.Periode(
                        fom = LocalDate.of(2026, 2, 4),
                        tom = LocalDate.of(2026, 2, 4),
                        posteringer = listOf(
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLSTDR,
                                sakId = SakId("200001495"),
                                fom = LocalDate.of(2026, 2, 4),
                                tom = LocalDate.of(2026, 2, 4),
                                beløp = 630,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSDRASISP1-OP"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLSTDR,
                                sakId = SakId("200001495"),
                                fom = LocalDate.of(2026, 2, 4),
                                tom = LocalDate.of(2026, 2, 4),
                                beløp = 340,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSDRASISP1-OP"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLSTDR,
                                sakId = SakId("200001495"),
                                fom = LocalDate.of(2026, 2, 4),
                                tom = LocalDate.of(2026, 2, 4),
                                beløp = 630,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_FEIL_TILLST"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLSTDR,
                                sakId = SakId("200001495"),
                                fom = LocalDate.of(2026, 2, 4),
                                tom = LocalDate.of(2026, 2, 4),
                                beløp = -630,
                                type = v1.PosteringType.MOTPOSTERING,
                                klassekode = "TBMOTOBS"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLSTDR,
                                sakId = SakId("200001495"),
                                fom = LocalDate.of(2026, 2, 4),
                                tom = LocalDate.of(2026, 2, 4),
                                beløp = -970,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSDRASISP1-OP"
                            ),
                        )
                    )
                )
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `simuler sak 200001495 produserer korrekt v2 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        runtime.soapRespondWith(Resource.read("/simuler-ts-200001495.xml"))

        val request = simulering(fagområde = "AAP")
        channel.trySend("aap-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.AAP]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("aap-key", key)

        val expected = v2.Simulering(
            perioder = listOf(
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2026, 2, 4),
                    tom = LocalDate.of(2026, 2, 4),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.TILLSTDR,
                            sakId = "200001495",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeTilleggsstønader.DAGLIG_REISE_AAP,
                            tidligereUtbetalt = 970,
                            nyttBeløp = -630,
                            posteringer = listOf(
                                v2.Postering(
                                    fom = LocalDate.of(2026, 2, 4),
                                    tom = LocalDate.of(2026, 2, 4),
                                    beløp = 630,
                                    type = v2.Type.YTEL,
                                    klassekode = "TSDRASISP1-OP"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2026, 2, 4),
                                    tom = LocalDate.of(2026, 2, 4),
                                    beløp = 340,
                                    type = v2.Type.YTEL,
                                    klassekode = "TSDRASISP1-OP"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2026, 2, 4),
                                    tom = LocalDate.of(2026, 2, 4),
                                    beløp = 630,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_FEIL_TILLST"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2026, 2, 4),
                                    tom = LocalDate.of(2026, 2, 4),
                                    beløp = -630,
                                    type = v2.Type.MOTP,
                                    klassekode = "TBMOTOBS"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2026, 2, 4),
                                    tom = LocalDate.of(2026, 2, 4),
                                    beløp = -970,
                                    type = v2.Type.YTEL,
                                    klassekode = "TSDRASISP1-OP"
                                ),
                            )
                        ),
                    )
                ),
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `simuler sak 4819 produserer korrekt v1 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        runtime.soapRespondWith(Resource.read("/simuler-ts-4819.xml"))

        val request = simulering(fagområde = "TILLST")
        channel.trySend("ts-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.TILLEGGSSTØNADER]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("ts-key", key)

        val expected = v1.Simulering(
            listOf(
                v1.OppsummeringForPeriode(
                    fom = LocalDate.of(2025, 8, 1),
                    tom = LocalDate.of(2025, 8, 1),
                    tidligereUtbetalt = 4505,
                    nyUtbetaling = 4293,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 212,
                ),
                v1.OppsummeringForPeriode(
                    fom = LocalDate.of(2026, 1, 27),
                    tom = LocalDate.of(2026, 1, 27),
                    tidligereUtbetalt = 0,
                    nyUtbetaling = 4605,
                    totalEtterbetaling = 4605,
                    totalFeilutbetaling = 0,
                )
            ),
            v1.SimuleringDetaljer(
                gjelderId = "12345678910",
                datoBeregnet = LocalDate.of(2026, 1, 20),
                totalBeløp = 4499,
                perioder = listOf(
                    v1.Periode(
                        fom = LocalDate.of(2025, 8, 1),
                        tom = LocalDate.of(2025, 8, 1),
                        posteringer = listOf(
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 1),
                                tom = LocalDate.of(2025, 8, 1),
                                beløp = 638,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_JUST_ARBYT"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 1),
                                tom = LocalDate.of(2025, 8, 1),
                                beløp = -638,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSTBASISP2-OP"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 1),
                                tom = LocalDate.of(2025, 8, 1),
                                beløp = 638,
                                type = v1.PosteringType.MOTPOSTERING,
                                klassekode = "TBMOTOBS"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 1),
                                tom = LocalDate.of(2025, 8, 1),
                                beløp = -638,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_FEIL_ARBYT"
                            ),
                        )
                    ),
                    v1.Periode(
                        fom = LocalDate.of(2025, 8, 11),
                        tom = LocalDate.of(2025, 8, 11),
                        posteringer = listOf(
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("4819"),
                                fom = LocalDate.of(2025, 8, 11),
                                tom = LocalDate.of(2025, 8, 11),
                                beløp = 3867,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_JUST_ARBYT"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("4819"),
                                fom = LocalDate.of(2025, 8, 11),
                                tom = LocalDate.of(2025, 8, 11),
                                beløp = -3867,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSLMASISP2-OP"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("4819"),
                                fom = LocalDate.of(2025, 8, 11),
                                tom = LocalDate.of(2025, 8, 11),
                                beløp = 3867,
                                type = v1.PosteringType.MOTPOSTERING,
                                klassekode = "TBMOTOBS"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("4819"),
                                fom = LocalDate.of(2025, 8, 11),
                                tom = LocalDate.of(2025, 8, 11),
                                beløp = -3867,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_FEIL_ARBYT"
                            ),
                        )
                    ),
                    v1.Periode(
                        fom = LocalDate.of(2025, 8, 27),
                        tom = LocalDate.of(2025, 8, 27),
                        posteringer = listOf(
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                beløp = -106,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_JUST_ARBYT"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                beløp = 106,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_FEIL_ARBYT"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                beløp = -106,
                                type = v1.PosteringType.MOTPOSTERING,
                                klassekode = "TBMOTOBS"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                beløp = 106,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_FEIL_ARBYT"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("223"),
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                beløp = -106,
                                type = v1.PosteringType.MOTPOSTERING,
                                klassekode = "TBMOTOBS"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("4819"),
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                beløp = -4505,
                                type = v1.PosteringType.FEILUTBETALING,
                                klassekode = "KL_KODE_JUST_ARBYT"
                            ),
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("4819"),
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                beløp = 4505,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSLMASISP2-OP"
                            ),
                        )
                    ),
                    v1.Periode(
                        fom = LocalDate.of(2026, 1, 27),
                        tom = LocalDate.of(2026, 1, 27),
                        posteringer = listOf(
                            v1.Postering(
                                fagområde = v1.Fagområde.TILLST,
                                sakId = SakId("4819"),
                                fom = LocalDate.of(2026, 1, 27),
                                tom = LocalDate.of(2026, 1, 27),
                                beløp = 4605,
                                type = v1.PosteringType.YTELSE,
                                klassekode = "TSLMASISP2-OP"
                            ),
                        )
                    ),
                ),
            )
        )
        assertEquals(expected, result)
    }

    @Test
    fun `simuler sak 4819 produserer korrekt v2 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = createProducers()
        val worker = SimuleringWorker(channel, service, producers)

        runtime.soapRespondWith(Resource.read("/simuler-ts-4819.xml"))

        val request = simulering(fagområde = "AAP")
        channel.trySend("aap-key" to request)

        runBlocking {
            val job = launch { worker.run() }
            Thread.sleep(100)
            channel.close()
            job.join()
        }

        val history = producers[Fagsystem.AAP]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("aap-key", key)

        val expected = v2.Simulering(
            perioder = listOf(
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2025, 8, 1),
                    tom = LocalDate.of(2025, 8, 1),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.TILLEGGSSTØNADER,
                            sakId = "223",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                            tidligereUtbetalt = 638,
                            nyttBeløp = -638,
                            posteringer = listOf(
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 1),
                                    tom = LocalDate.of(2025, 8, 1),
                                    beløp = 638,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_JUST_ARBYT"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 1),
                                    tom = LocalDate.of(2025, 8, 1),
                                    beløp = -638,
                                    type = v2.Type.YTEL,
                                    klassekode = "TSTBASISP2-OP"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 1),
                                    tom = LocalDate.of(2025, 8, 1),
                                    beløp = 638,
                                    type = v2.Type.MOTP,
                                    klassekode = "TBMOTOBS"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 1),
                                    tom = LocalDate.of(2025, 8, 1),
                                    beløp = -638,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_FEIL_ARBYT"
                                ),
                            )
                        )
                    )
                ),
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2025, 8, 11),
                    tom = LocalDate.of(2025, 8, 11),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.TILLEGGSSTØNADER,
                            sakId = "4819",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                            tidligereUtbetalt = 3867,
                            nyttBeløp = -3867,
                            posteringer = listOf(
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 11),
                                    tom = LocalDate.of(2025, 8, 11),
                                    beløp = 3867,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_JUST_ARBYT"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 11),
                                    tom = LocalDate.of(2025, 8, 11),
                                    beløp = -3867,
                                    type = v2.Type.YTEL,
                                    klassekode = "TSLMASISP2-OP"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 11),
                                    tom = LocalDate.of(2025, 8, 11),
                                    beløp = 3867,
                                    type = v2.Type.MOTP,
                                    klassekode = "TBMOTOBS"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 11),
                                    tom = LocalDate.of(2025, 8, 11),
                                    beløp = -3867,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_FEIL_ARBYT"
                                ),
                            )
                        )
                    )
                ),
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2025, 8, 27),
                    tom = LocalDate.of(2025, 8, 27),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.TILLEGGSSTØNADER,
                            sakId = "223",
                            utbetalesTil = "12345678910",
                            stønadstype = null,
                            tidligereUtbetalt = 0,
                            nyttBeløp = -212,
                            posteringer = listOf(
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 27),
                                    tom = LocalDate.of(2025, 8, 27),
                                    beløp = -106,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_JUST_ARBYT"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 27),
                                    tom = LocalDate.of(2025, 8, 27),
                                    beløp = 106,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_FEIL_ARBYT"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 27),
                                    tom = LocalDate.of(2025, 8, 27),
                                    beløp = -106,
                                    type = v2.Type.MOTP,
                                    klassekode = "TBMOTOBS"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 27),
                                    tom = LocalDate.of(2025, 8, 27),
                                    beløp = 106,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_FEIL_ARBYT"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 27),
                                    tom = LocalDate.of(2025, 8, 27),
                                    beløp = -106,
                                    type = v2.Type.MOTP,
                                    klassekode = "TBMOTOBS"
                                ),
                            )
                        ),
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.TILLEGGSSTØNADER,
                            sakId = "4819",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                            tidligereUtbetalt = 0,
                            nyttBeløp = 4505,
                            posteringer = listOf(
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 27),
                                    tom = LocalDate.of(2025, 8, 27),
                                    beløp = -4505,
                                    type = v2.Type.FEIL,
                                    klassekode = "KL_KODE_JUST_ARBYT"
                                ),
                                v2.Postering(
                                    fom = LocalDate.of(2025, 8, 27),
                                    tom = LocalDate.of(2025, 8, 27),
                                    beløp = 4505,
                                    type = v2.Type.YTEL,
                                    klassekode = "TSLMASISP2-OP"
                                ),
                            )
                        )
                    )
                ),
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2026, 1, 27),
                    tom = LocalDate.of(2026, 1, 27),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.TILLEGGSSTØNADER,
                            sakId = "4819",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                            tidligereUtbetalt = 0,
                            nyttBeløp = 4605,
                            posteringer = listOf(
                                v2.Postering(
                                    fom = LocalDate.of(2026, 1, 27),
                                    tom = LocalDate.of(2026, 1, 27),
                                    beløp = 4605,
                                    type = v2.Type.YTEL,
                                    klassekode = "TSLMASISP2-OP"
                                ),
                            )
                        )
                    )
                ),
            )
        )
        assertEquals(expected, result)
    }

    @Test
    fun `scheduler evicts entries older than evictionTtl`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)

        libs.kafka.Names.clear()
        val kafka = libs.kafka.StreamsMock()
        kafka.connect(
            topology = libs.kafka.topology {
                val ktable = consume(Tables.simuleringer)
                val scheduler = SimuleringScheduler(ktable, 5.milliseconds, channel, 2.minutes)
                ktable.schedule(scheduler)
            },
            config = kafka.config.copy(additionalProperties = java.util.Properties().apply {
                put(org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG,
                    org.apache.kafka.streams.state.BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
            }),
            registry = SimpleMeterRegistry(),
        )

        val topic = kafka.testInputTopic(Topics.simuleringer)
        topic.produce("key-1") { simulering(fagområde = "AAP") }

        // Advance past 2 minutes — should not be queued
        kafka.advanceWallClockTime(3.minutes)
        assertTrue(channel.tryReceive().isFailure, "Entry older than 2min should be evicted")
    }

    private fun createProducers(): Map<Fagsystem, KafkaProducerFake<String, Simulering>> {
        val kafka = runtime.kafka
        return mapOf(
            Fagsystem.AAP to kafka.createProducer(runtime.config.kafka, Topics.dryrunAap) as KafkaProducerFake<String, Simulering>,
            Fagsystem.DAGPENGER to kafka.createProducer(runtime.config.kafka, Topics.dryrunDp) as KafkaProducerFake<String, Simulering>,
            Fagsystem.TILLEGGSSTØNADER to kafka.createProducer(runtime.config.kafka, Topics.dryrunTs) as KafkaProducerFake<String, Simulering>,
            Fagsystem.TILTAKSPENGER to kafka.createProducer(runtime.config.kafka, Topics.dryrunTp) as KafkaProducerFake<String, Simulering>,
        )
    }

    private fun jaxbResponse(): String = Resource.read("/simuler-jaxb-response.xml")

    private fun soapFault(body: String): String = """
        <SOAP-ENV:Fault xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            $body
        </SOAP-ENV:Fault>
    """.trimIndent()
}
