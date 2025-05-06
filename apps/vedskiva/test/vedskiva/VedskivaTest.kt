package vedskiva

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.GregorianCalendar
import java.util.UUID
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import models.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.AksjonType
import no.trygdeetaten.skjema.oppdrag.*
import org.junit.jupiter.api.BeforeEach

class VedskivaTest {

    @BeforeEach
    fun reset() {
        database(TestRuntime.config)
        TestRuntime.reset()
    }

    @Test
    fun `can avstemme 00 (OK)`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 0,
                offset = 0,
                key = "abc",
                beløper = listOf(100),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(100, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(100, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `test with dev data from may 5th`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    Scheduled(LocalDate.now().minusDays(1), LocalDate.now().minusDays(2), LocalDate.now().minusDays(2)).insert()
                }
            }
        }
        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(6, avsProducer.history().size)

        assertEquals(AksjonType.START, avsProducer.history()[0].second.aksjon.aksjonType)
        val aap = avsProducer.history()[1].second

        assertEquals(7, aap.total.totalAntall)
        assertEquals(5455, aap.total.totalBelop.toInt())
        assertEquals(7, aap.grunnlag.godkjentAntall)
        assertEquals(5455, aap.grunnlag.godkjentBelop.toInt())
        assertEquals(0, aap.grunnlag.varselAntall)
        assertEquals(0, aap.grunnlag.varselBelop.toInt())
        assertEquals(0, aap.grunnlag.avvistAntall)
        assertEquals(0, aap.grunnlag.avvistBelop.toInt())
        assertEquals(0, aap.grunnlag.manglerAntall)
        assertEquals(0, aap.grunnlag.manglerBelop.toInt())
        assertEquals(AksjonType.AVSL, avsProducer.history()[2].second.aksjon.aksjonType)

        assertEquals(AksjonType.START, avsProducer.history()[3].second.aksjon.aksjonType)
        val tilt = avsProducer.history()[4].second
        assertEquals(4, tilt.total.totalAntall)
        assertEquals(2280, tilt.total.totalBelop.toInt())
        assertEquals(4, tilt.grunnlag.godkjentAntall)
        assertEquals(2280, tilt.grunnlag.godkjentBelop.toInt())
        assertEquals(0, tilt.grunnlag.varselAntall)
        assertEquals(0, tilt.grunnlag.varselBelop.toInt())
        assertEquals(0, tilt.grunnlag.avvistAntall)
        assertEquals(0, tilt.grunnlag.avvistBelop.toInt())
        assertEquals(0, tilt.grunnlag.manglerAntall)
        assertEquals(0, tilt.grunnlag.manglerBelop.toInt())
        assertEquals(AksjonType.AVSL, avsProducer.history()[5].second.aksjon.aksjonType)
    }

    @Test
    fun `can avstemme 04 (Varsel)`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("04"),
                partition = 0,
                offset = 0,
                key = "abc",
                beløper = listOf(10),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(10, data.total.totalBelop.toInt())
        assertEquals(0, data.grunnlag.godkjentAntall)
        assertEquals(0, data.grunnlag.godkjentBelop.toInt())
        assertEquals(1, data.grunnlag.varselAntall)
        assertEquals(10, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `can avstemme 08 (Funksjonell Feil)`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("08", "funksjonell", "feil"),
                partition = 0,
                offset = 0,
                key = "abc",
                beløper = listOf(1),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(1, data.total.totalBelop.toInt())
        assertEquals(0, data.grunnlag.godkjentAntall)
        assertEquals(0, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(1, data.grunnlag.avvistAntall)
        assertEquals(1, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `can avstemme 12 (Teknisk Feil)`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("12", "teknisk", "feil"),
                partition = 0,
                offset = 0,
                key = "abc",
                beløper = listOf(3),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(3, data.total.totalBelop.toInt())
        assertEquals(0, data.grunnlag.godkjentAntall)
        assertEquals(0, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(1, data.grunnlag.avvistAntall)
        assertEquals(3, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `can avstemme    (no kvittering)`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = null,
                partition = 0,
                offset = 0,
                key = "abc",
                beløper = listOf(1000),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(1000, data.total.totalBelop.toInt())
        assertEquals(0, data.grunnlag.godkjentAntall)
        assertEquals(0, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(1, data.grunnlag.manglerAntall)
        assertEquals(1000, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }
    
    @Test
    fun `can skip without avstemminger for today`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 1,
                offset = 6,
                key = "avstemmes in 1 day",
                beløper = listOf(400),
                avstemmingdag = LocalDate.now().plusDays(1),
            )
        )

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 0,
                offset = 2,
                key = "avstemt 1 day ago",
                beløper = listOf(500),
                avstemmingdag = LocalDate.now().minusDays(1),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(0, avsProducer.history().size)
    }

    @Test
    fun `can deduplicate`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        val dao = dao(
            kvittering = mmel("00"),
            partition = 0,
            offset = 0,
            key = "abc",
            beløper = listOf(100),
        )
        PeisschtappernFake.response.add(dao)
        PeisschtappernFake.response.add(dao)
        PeisschtappernFake.response.add(dao.copy(offset = 1))

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(100, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(100, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `can accumulate oppdrag with same key`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 0,
                offset = 1,
                key = "to be accumulated",
                beløper = listOf(333),
            )
        )
        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 0,
                offset = 2,
                key = "to be accumulated",
                beløper = listOf(666),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(2, data.total.totalAntall)
        assertEquals(999, data.total.totalBelop.toInt())
        assertEquals(2, data.grunnlag.godkjentAntall)
        assertEquals(999, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `can summarize total beløp per oppdrag`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 0,
                offset = 0,
                key = "abc",
                beløper = listOf(100, 200, 300),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(600, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(600, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `can accumulate opphør (UPDATE)`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            Dao(
                version = "v1",
                topic_name = "helved.oppdrag.v1",
                partition = 0,
                offset = 0,
                key = "opphør",
                value = xmlMapper.writeValueAsString(
                    oppdrag(
                        mmel = mmel("00"),
                        satser = listOf(200),
                        kodeEndring = "NY",
                        avstemmingstidspunkt = LocalDate.now().atStartOfDay(),
                        oppdragslinjer = listOf(
                            oppdragslinje(
                                kodeEndring = "NY",
                                delytelsesId = "1",
                                sats = 200,
                                datoVedtakFom = LocalDate.of(2025, 11, 3),
                                datoVedtakTom = LocalDate.of(2025, 11, 7),
                                typeSats = "DAG",
                                henvisning = UUID.randomUUID().toString().drop(10),
                            )
                        )
                    )
                ), 
                timestamp_ms = Instant.now().toEpochMilli(),
                stream_time_ms = Instant.now().toEpochMilli(),
                system_time_ms = Instant.now().toEpochMilli(),
            )
        )
        PeisschtappernFake.response.add(
            Dao(
                version = "v1",
                topic_name = "helved.oppdrag.v1",
                partition = 0,
                offset = 1,
                key = "opphør",
                value = xmlMapper.writeValueAsString(
                    oppdrag(
                        mmel = mmel("00"),
                        satser = listOf(200),
                        kodeEndring = "ENDR",
                        avstemmingstidspunkt = LocalDate.now().atStartOfDay(),
                        oppdragslinjer = listOf(
                            oppdragslinje(
                                kodeEndring = "OPPH",
                                delytelsesId = "1",
                                sats = 200,
                                datoVedtakFom = LocalDate.of(2025, 11, 3),
                                datoVedtakTom = LocalDate.of(2025, 11, 7),
                                typeSats = "DAG",
                                henvisning = UUID.randomUUID().toString().drop(10),
                            )
                        )
                    )
                ), 
                timestamp_ms = Instant.now().toEpochMilli(),
                stream_time_ms = Instant.now().toEpochMilli(),
                system_time_ms = Instant.now().toEpochMilli(),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(2, data.total.totalAntall)
        assertEquals(400, data.total.totalBelop.toInt())
        assertEquals(2, data.grunnlag.godkjentAntall)
        assertEquals(400, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `can accumulate opphør (DELETE)`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            Dao(
                version = "v1",
                topic_name = "helved.oppdrag.v1",
                partition = 0,
                offset = 0,
                key = "opphør",
                value = xmlMapper.writeValueAsString(
                    oppdrag(
                        mmel = mmel("00"),
                        satser = listOf(200),
                        kodeEndring = "NY",
                        avstemmingstidspunkt = LocalDate.now().atStartOfDay(),
                        oppdragslinjer = listOf(
                            oppdragslinje(
                                kodeEndring = "NY",
                                delytelsesId = "1",
                                sats = 200,
                                datoVedtakFom = LocalDate.of(2025, 11, 3),
                                datoVedtakTom = LocalDate.of(2025, 11, 7),
                                typeSats = "DAG",
                                henvisning = UUID.randomUUID().toString().drop(10),
                            )
                        )
                    )
                ), 
                timestamp_ms = Instant.now().toEpochMilli(),
                stream_time_ms = Instant.now().toEpochMilli(),
                system_time_ms = Instant.now().toEpochMilli(),
            )
        )
        PeisschtappernFake.response.add(
            Dao(
                version = "v1",
                topic_name = "helved.oppdrag.v1",
                partition = 0,
                offset = 1,
                key = "opphør",
                value = xmlMapper.writeValueAsString(
                    oppdrag(
                        mmel = mmel("00"),
                        satser = listOf(200),
                        kodeEndring = "NY",
                        avstemmingstidspunkt = LocalDate.now().atStartOfDay(),
                        oppdragslinjer = listOf(
                            oppdragslinje(
                                kodeEndring = "OPPH",
                                delytelsesId = "1",
                                sats = 200,
                                datoVedtakFom = LocalDate.of(2025, 11, 3),
                                datoVedtakTom = LocalDate.of(2025, 11, 7),
                                typeSats = "DAG",
                                henvisning = UUID.randomUUID().toString().drop(10),
                            )
                        )
                    )
                ), 
                timestamp_ms = Instant.now().toEpochMilli(),
                stream_time_ms = Instant.now().toEpochMilli(),
                system_time_ms = Instant.now().toEpochMilli(),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(2, data.total.totalAntall)
        assertEquals(400, data.total.totalBelop.toInt())
        assertEquals(2, data.grunnlag.godkjentAntall)
        assertEquals(400, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `will take precedence on kvitterte oppdrag`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = null,
                partition = 0,
                offset = 1,
                key = "to be kvittert",
                beløper = listOf(333),
            )
        )
        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 0,
                offset = 2,
                key = "to be kvittert",
                beløper = listOf(333),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(3, avsProducer.history().size)
        val start = avsProducer.history()[0].second
        assertEquals(AksjonType.START, start.aksjon.aksjonType)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(333, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(333, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
        val end = avsProducer.history()[2].second
        assertEquals(AksjonType.AVSL, end.aksjon.aksjonType)
    }

    @Test
    fun `will skip future avstemminger`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 2,
                offset = 6,
                key = "avstemmes in 1 day",
                beløper = listOf(400),
                avstemmingdag = LocalDate.now().plusDays(1),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(0, avsProducer.history().size)
    }

    @Test
    fun `will skip previous avstemminger`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 1,
                offset = 2,
                key = "avstemt 3 days ago",
                beløper = listOf(4000),
                avstemmingdag = LocalDate.now().minusDays(3),
            )
        )

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(0, avsProducer.history().size)
    }

    @Test
    fun `will save scheduled`() = runTest(TestRuntime.context) {
        TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming).use { avsProducer ->
            PeisschtappernFake.response.add(
                dao(
                    kvittering = mmel("00"),
                    partition = 0,
                    offset = 0,
                    key = "abc",
                    beløper = listOf(100),
                )
            )

            vedskiva(TestRuntime.config, TestRuntime.kafka)
            assertEquals(3, avsProducer.history().size)
        }

        TestRuntime.kafka.reset()

        TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming).use { avsProducer ->
            vedskiva(TestRuntime.config, TestRuntime.kafka)
            assertEquals(0, avsProducer.history().size)
        }
    }

    @Test
    fun `has idempotent scheduler`() = runTest(TestRuntime.context) {
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        PeisschtappernFake.response.add(
            dao(
                kvittering = mmel("00"),
                partition = 0,
                offset = 0,
                key = "abc",
                beløper = listOf(100),
            )
        )

        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    Scheduled(LocalDate.now(), LocalDate.now(), LocalDate.now()).insert()
                }
            }
        }

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(0, avsProducer.history().size)
    }
}

private val xmlMapper: libs.xml.XMLMapper<Oppdrag> = libs.xml.XMLMapper()
private val objectFactory = ObjectFactory()
private fun LocalDateTime.format() = truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
private fun LocalDate.toXMLDate(): XMLGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))

private fun dao(
    kvittering: Mmel? = mmel(),
    partition: Int = 0,
    offset: Int = 0,
    key: String = UUID.randomUUID().toString(),
    beløper: List<Int>? = null,
    avstemmingdag: LocalDate = LocalDate.now(),
): Dao = Dao(
    version = "v1",
    topic_name = "helved.oppdrag.v1",
    key = key,
    value = beløper?.let{ 
        xmlMapper.writeValueAsString(
            oppdrag(
                mmel = kvittering,
                satser = it,
                avstemmingstidspunkt = avstemmingdag.atStartOfDay(),
            )
        ) 
    },
    partition = partition,
    offset = offset.toLong(),
    timestamp_ms = Instant.now().toEpochMilli(),
    stream_time_ms = Instant.now().toEpochMilli(),
    system_time_ms = Instant.now().toEpochMilli(),
)

private fun mmel(
    alvorlighetsgrad: String = "00", // 00/04/08/12
    kodeMelding: String? = null,
    beskrMelding: String? = null,
): Mmel = Mmel().apply {
    this.alvorlighetsgrad = alvorlighetsgrad
    this.kodeMelding = kodeMelding 
    this.beskrMelding = beskrMelding 
}

private fun oppdrag(
    satser: List<Int>,
    oppdragslinjer: List<OppdragsLinje150> = satser.mapIndexed { idx, sats ->
        oppdragslinje(
            delytelsesId = "$idx",                              // periodeId
            sats = sats.toLong(),                               // beløp
            datoVedtakFom = LocalDate.of(2025, 11, 3),          // fom
            datoVedtakTom = LocalDate.of(2025, 11, 7),          // tom
            typeSats = "DAG",                                   // periodetype
            henvisning = UUID.randomUUID().toString().drop(10), // behandlingId
        )
    },
    kodeEndring: String = "NY",                                 // NY/ENDR
    fagområde: String = "AAP",
    fagsystemId: String = "1",                                  // sakid
    oppdragGjelderId: String = "12345678910",                   // personident 
    saksbehId: String = "Z999999",
    avstemmingstidspunkt: LocalDateTime = LocalDateTime.now(),
    enhet: String? = null,
    mmel: Mmel? = mmel(),
) = objectFactory.createOppdrag().apply {
    this.mmel = mmel
    this.oppdrag110 = objectFactory.createOppdrag110().apply {
        this.kodeAksjon = "1"
        this.kodeEndring = kodeEndring
        this.kodeFagomraade = fagområde
        this.fagsystemId = fagsystemId
        this.utbetFrekvens = "MND"
        this.oppdragGjelderId = oppdragGjelderId
        this.datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
        this.saksbehId = saksbehId
        this.avstemming115 = objectFactory.createAvstemming115().apply {
            kodeKomponent = fagområde
            nokkelAvstemming = avstemmingstidspunkt.format()
            tidspktMelding = avstemmingstidspunkt.format()
        }
        enhet?.let {
            listOf(
                objectFactory.createOppdragsEnhet120().apply {
                    this.enhet = enhet
                    this.typeEnhet = "BOS"
                    this.datoEnhetFom = LocalDate.of(1970, 1, 1).toXMLDate()
                },
                objectFactory.createOppdragsEnhet120().apply {
                    this.enhet = "8020"
                    this.typeEnhet = "BEH"
                    this.datoEnhetFom = LocalDate.of(1900, 1, 1).toXMLDate()
                },
            )
        } ?: listOf(
            objectFactory.createOppdragsEnhet120().apply {
                this.enhet = "8020"
                this.typeEnhet = "BOS"
                this.datoEnhetFom = LocalDate.of(1900, 1, 1).toXMLDate()
            },
        ).forEach(oppdragsEnhet120s::add)
        oppdragsLinje150s.addAll(oppdragslinjer)
    }
}

private fun oppdragslinje(
    delytelsesId: String,
    sats: Long,
    datoVedtakFom: LocalDate,
    datoVedtakTom: LocalDate,
    typeSats: String,                       // DAG/DAG7/MND/ENG
    henvisning: String,                     // behandlingId
    refDelytelsesId: String? = null,        // lastPeriodeId
    kodeEndring: String = "NY",             // NY/ENDR
    opphør: LocalDate? = null,
    fagsystemId: String = "1",              // sakId
    vedtakId: LocalDate = LocalDate.now(),  // vedtakstidspunkt
    klassekode: String = "AAPUAA",
    saksbehId: String = "Z999999",          // saksbehandler
    beslutterId: String = "Z999999",        // beslutter
    utbetalesTilId: String = "12345678910", // personident
    vedtakssats: Long? = null,              // fastsattDagsats
): OppdragsLinje150 {
    val attestant = objectFactory.createAttestant180().apply {
        attestantId = beslutterId
    }

    return objectFactory.createOppdragsLinje150().apply {
        kodeEndringLinje = kodeEndring
        opphør?.let {
            kodeStatusLinje = TkodeStatusLinje.OPPH
            datoStatusFom = opphør.toXMLDate()
        }
        if (kodeEndring == "ENDR") {
            refDelytelsesId?.let {
                refDelytelseId = refDelytelsesId
                refFagsystemId = fagsystemId
            }
        }
        this.vedtakId = vedtakId.toString()
        this.delytelseId = delytelsesId
        this.kodeKlassifik = klassekode
        this.datoVedtakFom = datoVedtakFom.toXMLDate()
        this.datoVedtakTom = datoVedtakTom.toXMLDate()
        this.sats = BigDecimal.valueOf(sats)
        this.fradragTillegg = TfradragTillegg.T
        this.typeSats = typeSats
        this.brukKjoreplan = "N"
        this.saksbehId = saksbehId
        this.utbetalesTilId = utbetalesTilId
        this.henvisning = henvisning
        attestant180s.add(attestant)

        vedtakssats?.let {
            vedtakssats157 = objectFactory.createVedtakssats157().apply {
                this.vedtakssats = BigDecimal.valueOf(vedtakssats)
            }
        }
    }
}


