package vedskiva

import java.time.LocalDate
import java.util.UUID
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
import org.junit.jupiter.api.BeforeEach

class VedskivaTest {

    @BeforeEach
    fun reset() {
        database(TestRuntime.config)
        TestRuntime.reset()
    }

    @Test
    fun `can avstemme AAP`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        oppConsumer.assign(0, 1, 2)

        val funkFeil = oppdragsdata(kvittering = Kvittering("hei", "08", "ho"), totalBeløpAllePerioder = 1u) 
        val ingenKvitt = oppdragsdata(kvittering = null, totalBeløpAllePerioder = 1000u)
        val okVarsel = oppdragsdata(kvittering = Kvittering("beskjed", "04", "yo"), totalBeløpAllePerioder = 10u)
        val okFremtid = oppdragsdata(avstemmingsdag = LocalDate.now().plusDays(1), totalBeløpAllePerioder = 10000u)
        val ok = oppdragsdata(totalBeløpAllePerioder = 100u)
        val tekFeil = oppdragsdata(kvittering = Kvittering("wopsie", "12", "deisy"), totalBeløpAllePerioder = 3u)

        oppConsumer.populate("5", ok,         0, 0L)
        oppConsumer.populate("6", tekFeil,    0, 1L)
        oppConsumer.populate("7", ok,         0, 2L)
        oppConsumer.populate("7", null,       0, 3L)
        oppConsumer.populate("1", funkFeil,   1, 0L)
        oppConsumer.populate("2", ingenKvitt, 1, 1L)
        oppConsumer.populate("3", okVarsel,   2, 0L)
        oppConsumer.populate("4", okFremtid,  2, 1L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(5, oppProducer.history().size)
        oppProducer.history().forEach { (_, value) -> assertNull(value) }

        assertEquals(3, avsProducer.history().size)
        assertEquals(AksjonType.START, avsProducer.history().first().second.aksjon.aksjonType)
        assertEquals(AksjonType.AVSL, avsProducer.history().last().second.aksjon.aksjonType)

        val data = avsProducer.history()[1].second
        assertEquals(AksjonType.DATA, data.aksjon.aksjonType)
        assertEquals(5, data.total.totalAntall)
        assertEquals(1114, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(100, data.grunnlag.godkjentBelop.toInt())
        assertEquals(1, data.grunnlag.varselAntall)
        assertEquals(10, data.grunnlag.varselBelop.toInt())
        assertEquals(2, data.grunnlag.avvistAntall)
        assertEquals(4, data.grunnlag.avvistBelop.toInt())
        assertEquals(1, data.grunnlag.manglerAntall)
        assertEquals(1000, data.grunnlag.manglerBelop.toInt())

        // console out avstemming XML
        avsProducer.history().forEach { (_, value) ->
            testLog.debug(Topics.avstemming.serdes.value.serializer().serialize(Topics.avstemming.name, value).decodeToString())
        }
    }

    @Test
    fun `has idempotent scheduler`() = runTest(TestRuntime.context) {
        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    Scheduled(LocalDate.now(), LocalDate.now(), LocalDate.now()).insert()
                }
            }
        }

        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        oppConsumer.assign(0, 1, 2)
        val okFremtid = oppdragsdata(avstemmingsdag = LocalDate.now().plusDays(1), totalBeløpAllePerioder = 10000u)
        oppConsumer.populate("1", okFremtid, 0, 0L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(0, oppProducer.history().size)
        assertEquals(0, avsProducer.history().size)
    }

    @Test
    fun `can skip avstemming without scheduled oppdragsdatas`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)

        oppConsumer.assign(0, 1, 2)
        val okFremtid = oppdragsdata(avstemmingsdag = LocalDate.now().plusDays(1), totalBeløpAllePerioder = 10000u)
        oppConsumer.populate("1", okFremtid, 0, 0L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(0, oppProducer.history().size)
        assertEquals(0, avsProducer.history().size)
    }

    @Test
    fun `can filter tombstoned oppdragsdata`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)
        oppConsumer.assign(0, 1, 2)
        val ok = oppdragsdata(totalBeløpAllePerioder = 100u)

        oppConsumer.populate("7", ok,   0, 2L)
        oppConsumer.populate("7", null, 0, 3L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(0, oppProducer.history().size)
        assertEquals(0, avsProducer.history().size)
    }

    @Test
    fun `can deduplicate`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)
        oppConsumer.assign(0, 1, 2)
        val ok = oppdragsdata(totalBeløpAllePerioder = 100u)

        oppConsumer.populate("1", ok, 0, 2L)
        oppConsumer.populate("1", ok, 0, 3L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(1, oppProducer.history().size)
        assertEquals(3, avsProducer.history().size)
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
    }

    @Test
    fun `can read records after tombstones`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)
        oppConsumer.assign(0, 1, 2)
        val okGammel = oppdragsdata(totalBeløpAllePerioder = 300u)
        val okNy = oppdragsdata(totalBeløpAllePerioder = 40u)

        oppConsumer.populate("7", okGammel, 0, 2L)
        oppConsumer.populate("7", null,     0, 3L)
        oppConsumer.populate("7", okNy,     0, 4L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(1, oppProducer.history().size)
        assertEquals(3, avsProducer.history().size)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(40, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(40, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
    }

    @Test
    fun `oppdragsdata with kvittering takes precedence`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)
        oppConsumer.assign(0, 1, 2)
        val okMedKvitt = oppdragsdata(totalBeløpAllePerioder = 50u)
        val okUtenKvitt = okMedKvitt.copy(kvittering = null)

        oppConsumer.populate("8", okUtenKvitt, 0, 1L)
        oppConsumer.populate("8", okMedKvitt,  0, 2L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(1, oppProducer.history().size)
        assertEquals(3, avsProducer.history().size)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(50, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(50, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
    }

    @Test
    fun `can accumulate oppdragsdata for same key`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)
        oppConsumer.assign(0, 1, 2)
        val okA = oppdragsdata(totalBeløpAllePerioder = 333u)
        val okB = oppdragsdata(totalBeløpAllePerioder = 666u)

        oppConsumer.populate("7", okA, 0, 2L)
        oppConsumer.populate("7", okB, 0, 3L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(1, oppProducer.history().size)
        assertEquals(3, avsProducer.history().size)
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
    }

    @Test
    fun `test case AAP`() = runTest(TestRuntime.context) {
        val oppConsumer = TestRuntime.kafka.createConsumer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val oppProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.oppdragsdata)
        val avsProducer = TestRuntime.kafka.createProducer(TestRuntime.config.kafka, Topics.avstemming)
        oppConsumer.assign(0, 1, 2)

        val oppdragsdata = oppdragsdata(
            Fagsystem.AAP,
            Personident("23519035766"),
            SakId("4Mi993K"),
            "lZGWmiVzR0q4W52S++5nPQ==",
            LocalDate.of(2025, 4, 22),
            974u,
            Kvittering(null, "00", null)

        )
        oppConsumer.populate("7", oppdragsdata, 0, 2L)

        vedskiva(TestRuntime.config, TestRuntime.kafka)

        assertEquals(1, oppProducer.history().size)
        assertEquals(3, avsProducer.history().size)
        val data = avsProducer.history()[1].second
        assertEquals(1, data.total.totalAntall)
        assertEquals(974, data.total.totalBelop.toInt())
        assertEquals(1, data.grunnlag.godkjentAntall)
        assertEquals(974, data.grunnlag.godkjentBelop.toInt())
        assertEquals(0, data.grunnlag.varselAntall)
        assertEquals(0, data.grunnlag.varselBelop.toInt())
        assertEquals(0, data.grunnlag.avvistAntall)
        assertEquals(0, data.grunnlag.avvistBelop.toInt())
        assertEquals(0, data.grunnlag.manglerAntall)
        assertEquals(0, data.grunnlag.manglerBelop.toInt())
    }
}

private fun oppdragsdata(
    fagsystem: Fagsystem = Fagsystem.AAP,
    personident: Personident = Personident("12345678910"),
    sakId: SakId = SakId("1"),
    lastDelytelseId: String = UUID.randomUUID().toString(), 
    avstemmingsdag: LocalDate = LocalDate.now(),
    totalBeløpAllePerioder: UInt = 100u,
    kvittering: Kvittering? = Kvittering(null, "00", null), 
) = Oppdragsdata(
    fagsystem,
    personident,
    sakId,
    lastDelytelseId,
    avstemmingsdag,
    totalBeløpAllePerioder,
    kvittering, 
)

