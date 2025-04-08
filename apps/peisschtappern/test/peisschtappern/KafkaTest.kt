package peisschtappern

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

    @Test
    fun `save kvittering-queue`() = runTest(TestRuntime.context) {
        TestTopics.kvitteringQueue.produce("123") {
            "test save kvittering_queue".toByteArray()
        }

        val dao = awaitDatabase(100) {
            Dao.find("123", Tables.kvittering_queue).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(Topics.kvitteringQueue.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("test save kvittering_queue", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }

    @Test
    fun `save simuleringer`() = runTest(TestRuntime.context) {
        TestTopics.simuleringer.produce("123") {
            "test save simuleringer".toByteArray()
        }

        val dao = awaitDatabase(100) {
            Dao.find("123", Tables.simuleringer).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(Topics.simuleringer.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("test save simuleringer", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }

    @Test
    fun `save utbetalinger`() = runTest(TestRuntime.context) {
        TestTopics.utbetalinger.produce("123") {
            "test save utbetalinger".toByteArray()
        }

        val dao = awaitDatabase(100) {
            Dao.find("123", Tables.utbetalinger).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(Topics.utbetalinger.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("test save utbetalinger", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }

    @Test
    fun `save saker`() = runTest(TestRuntime.context) {
        TestTopics.saker.produce("123") {
            "test save saker".toByteArray()
        }

        val dao = awaitDatabase(100) {
            Dao.find("123", Tables.saker).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(Topics.saker.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("test save saker", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }

    @Test
    fun `save aap`() = runTest(TestRuntime.context) {
        TestTopics.aap.produce("123") {
            "test save aap".toByteArray()
        }

        val dao = awaitDatabase(100) {
            Dao.find("123", Tables.aap).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(Topics.aap.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("test save aap", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }
}
