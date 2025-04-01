package peisstchappern

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNotNull

class KafkaTest {

    @Test
    fun `save oppdrag`() = runTest(TestRuntime.context) {
        TestTopics.oppdrag.produce("123") {
            "test save oppdrag".toByteArray()
        }

        val dao = awaitDatabase(100) {
            Dao.find("123", Tables.oppdrag).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(Topics.oppdrag.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("test save oppdrag", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }

    @Test
    fun `save kvittering`() = runTest(TestRuntime.context) {
        TestTopics.kvittering.produce("123") {
            "test save kvittering".toByteArray()
        }

        val dao = awaitDatabase(100) {
            Dao.find("123", Tables.kvittering).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(Topics.kvittering.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("test save kvittering", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }
}
