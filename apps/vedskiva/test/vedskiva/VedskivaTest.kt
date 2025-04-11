package vedskiva 

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import libs.postgres.Jdbc
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
    fun `avstem for AAP`() = runTest(TestRuntime.context) {
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
    fun `has already avstemt idempotency`() = runTest(TestRuntime.context) {
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
    fun `will skip avstemming when no scheduled oppdragsdatas are found`() = runTest(TestRuntime.context) {
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

}

private fun oppdragsdata(
    fagsystem: Fagsystem = Fagsystem.AAP,
    personident: Personident = Personident("12345678910"),
    sakId: SakId = SakId("1"),
    avstemmingsdag: LocalDate = LocalDate.now(),
    totalBeløpAllePerioder: UInt = 100u,
    kvittering: Kvittering? = Kvittering(null, "00", null), 
) = Oppdragsdata(
    fagsystem,
    personident,
    sakId,
    avstemmingsdag,
    totalBeløpAllePerioder,
    kvittering, 
)

