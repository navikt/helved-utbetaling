package peisschtappern

import kotlinx.coroutines.test.runTest
import libs.jdbc.*
import libs.kafka.TestTopic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNotNull

class KafkaTest {
    data class TestCase(
        val channel: Channel,
        val testTopic: TestTopic.InputOutput<String, ByteArray>,
    )

    private val testCases: List<TestCase> by lazy {
        Channel.all().map {
            when (it) {
                Channel.Avstemming -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Oppdrag -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Simuleringer -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Utbetalinger -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Saker -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Aap -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.DryrunAap -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.DryrunTp -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.DryrunTs -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.DryrunDp -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Status -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.PendingUtbetalinger -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Fk -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Dp -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.DpIntern -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.TsIntern -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.TpIntern -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Ts -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.AapIntern -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.Historisk -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
                Channel.HistoriskIntern -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
            }
        }
    }

    @Test
    fun `consume and save daos`() = runTest(TestRuntime.context) {
        testCases.forEach { case ->
            val byteArray = if (case.channel.topic == Topics.oppdrag) {
                TestData.oppdragXml().toByteArray()
            } else {
                """{"sakId": "123"}""".toByteArray()
            }
            val key = if (case.channel.topic == Topics.saker) {
                """{"sakId":"123","fagsystem":"TILTPENG"}"""
            } else {
                UUID.randomUUID().toString()
            }
            case.testTopic.produce(key) {
                byteArray
            }
            val dao = TestRuntime.jdbc.await(100) {
                Daos.find(key, case.channel.table).singleOrNull()
            }

            assertNotNull(dao)
            assertEquals("v1", dao.version)
            assertEquals(case.channel.topic.name, dao.topic_name)
            assertEquals(key, dao.key)
            assertEquals(String(byteArray), dao.value)
            assertNotNull(dao.timestamp_ms)
            assertNotNull(dao.stream_time_ms)
            assertNotNull(dao.system_time_ms)
            assertEquals("test", dao.commit)
        }
    }

    @Test
    fun `saves messages with invalid json`() = runTest(TestRuntime.context) {
        val case = TestCase(Channel.Aap, TestRuntime.kafka.testTopic(Channel.Aap.topic))
        val key = UUID.randomUUID().toString()
        val value = """{ ikke gyldig json }"""

        case.testTopic.produce(key) { value.toByteArray() }

        val dao = TestRuntime.jdbc.await(100) {
            Daos.find(case.channel.table, 1, listOf(key)).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(case.channel.topic.name, dao.topic_name)
        assertEquals(key, dao.key)
        assertEquals(value, dao.value)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
        assertEquals("test", dao.commit)
    }
}
