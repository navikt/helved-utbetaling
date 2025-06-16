package peisschtappern

import kotlinx.coroutines.test.runTest
import libs.kafka.TestTopic
import org.junit.jupiter.api.Assertions.assertEquals
import libs.jdbc.*
import org.junit.jupiter.api.Test
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
               Channel.Kvittering -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.Simuleringer -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.Utbetalinger -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.Saker -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.Aap -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.DryrunAap -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.DryrunTp -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.DryrunTs -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.DryrunDp -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
               Channel.Status -> TestCase(it, TestRuntime.kafka.testTopic(it.topic))
            }
        }
    }

    @Test
    fun `consume and save daos`() = runTest(TestRuntime.context) {
        testCases.forEach { case ->
            case.testTopic.produce("123") {
                "content for ${case.channel.table.name}".toByteArray()
            }
            val dao = TestRuntime.jdbc.await(100) {
                Dao.find("123", case.channel.table).singleOrNull()
            }

            assertNotNull(dao)
            assertEquals("v1", dao.version)
            assertEquals(case.channel.topic.name, dao.topic_name)
            assertEquals("123", dao.key)
            assertEquals("content for ${case.channel.table.name}", dao.value)
            assertEquals(0, dao.partition)
            assertEquals(0, dao.offset)
            assertNotNull(dao.timestamp_ms)
            assertNotNull(dao.stream_time_ms)
            assertNotNull(dao.system_time_ms)
        }
    }
}
