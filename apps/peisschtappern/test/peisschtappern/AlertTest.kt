package peisschtappern

import java.util.UUID
import kotlinx.coroutines.test.runTest
import libs.jdbc.await
import libs.jdbc.awaitNull
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import kotlin.test.Test
import kotlin.test.assertEquals

class AlertTest {

    @Test
    fun `legg til timer for oppdrag uten kvittering`() = runTest(TestRuntime.context) {
        val xmlMapper = XMLMapper<Oppdrag>()
        val oppdrag = xmlMapper.readValue(TestData.oppdragXml())
        val key = UUID.randomUUID().toString()

        val oppdragProducer = TestRuntime.kafka.testTopic(Topics.oppdrag)
        oppdragProducer.produce(key) { xmlMapper.writeValueAsBytes(oppdrag) }

        val dao = TestRuntime.jdbc.await(1000) {
           TimerDao.find(key)
        }

        assertEquals(key, dao?.key)
    }

    @Test
    fun `fjern timer for oppdrag når vi får kvittering`() = runTest(TestRuntime.context) {
        val xmlMapper = XMLMapper<Oppdrag>()
        val oppdrag = xmlMapper.readValue(TestData.oppdragXml())
        val key = UUID.randomUUID().toString()
        val oppdragMedKvittering = xmlMapper.readValue(TestData.oppdragXml(alvorlighetsgrad = "00"))

        val oppdragProducer = TestRuntime.kafka.testTopic(Topics.oppdrag)
        oppdragProducer.produce(key) { xmlMapper.writeValueAsBytes(oppdrag) }

        val dao = TestRuntime.jdbc.await(1000) {
            TimerDao.find(key)
        }

        assertEquals(key, dao?.key)
        oppdragProducer.produce(key) { xmlMapper.writeValueAsBytes(oppdragMedKvittering) }

        val dao2 = TestRuntime.jdbc.awaitNull(1000) {
            TimerDao.find(key)
        }
        assertEquals(null, dao2)

    }

    @Test
    fun `fjern timer for oppdrag dersom oppdrag er null`() = runTest(TestRuntime.context) {
        val xmlMapper = XMLMapper<Oppdrag>()
        val oppdrag = xmlMapper.readValue(TestData.oppdragXml())
        val key = UUID.randomUUID().toString()

        val oppdragProducer = TestRuntime.kafka.testTopic(Topics.oppdrag)
        oppdragProducer.produce(key) { xmlMapper.writeValueAsBytes(oppdrag) }

        val dao = TestRuntime.jdbc.await(1000) {
            TimerDao.find(key)
        }

        assertEquals(key, dao?.key)
        oppdragProducer.tombstone(key)

        val dao2 = TestRuntime.jdbc.awaitNull(1000) {
            TimerDao.find(key)
        }
        assertEquals(null, dao2)
    }
}
