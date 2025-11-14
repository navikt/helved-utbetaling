package libs.kafka.processor

import libs.kafka.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DedupProcessorTest {

    @AfterEach
    fun cleanup() {
        Names.clear()
    }

    @Test
    fun `can dedup`() {
        val kafka = Mock.withTopology {
            consume(Topics.B)
            .processor(
                DedupProcessor.supplier(
                    serdes = Tables.B.serdes,
                    retention = 10.milliseconds,
                    stateStoreName = "test-dedup-store",
                ){
                    // no nothing to succeed
                }
            ).produce(Topics.C)
        }
        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.inputTopic(Topics.B).produce("1", "hello")

        val records = kafka.outputTopic(Topics.C).readRecordsToList()
        assertEquals(1, records.size)
    }

    @Test
    fun `reset after retention`() {
        val kafka = Mock.withTopology {
            consume(Topics.B)
            .processor(
                DedupProcessor.supplier(
                    serdes = Tables.B.serdes,
                    retention = 10.milliseconds,
                    stateStoreName = "test-dedup-store",
                ){
                    // no nothing to succeed
                }
            ).produce(Topics.C)
        }
        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.advanceWallClockTime(11.toDuration(DurationUnit.MILLISECONDS))
        kafka.inputTopic(Topics.B).produce("1", "hello")

        val records = kafka.outputTopic(Topics.C).readRecordsToList()
        assertEquals(2, records.size)
    }

    @Test
    fun `will not dedup if value is different`() {
        val kafka = Mock.withTopology {
            consume(Topics.B)
            .processor(
                DedupProcessor.supplier(
                    serdes = Tables.B.serdes,
                    retention = 10.milliseconds,
                    stateStoreName = "test-dedup-store",
                    { _, value -> value.hashCode() }
                ){
                    // no nothing to succeed
                }
            ).produce(Topics.C)
        }
        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.inputTopic(Topics.B).produce("1", "there")

        val records = kafka.outputTopic(Topics.C).readRecordsToList()
        assertEquals(2, records.size)
    }

    @Test
    fun `will dedup if key is same`() {
        val kafka = Mock.withTopology {
            consume(Topics.B)
            .processor(
                DedupProcessor.supplier(
                    serdes = Tables.B.serdes,
                    retention = 10.milliseconds,
                    stateStoreName = "test-dedup-store",
                    { key, _ -> key.hashCode() }
                ){
                    // no nothing to succeed
                }
            ).produce(Topics.C)
        }
        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.inputTopic(Topics.B).produce("1", "hello")

        val records = kafka.outputTopic(Topics.C).readRecordsToList()
        assertEquals(1, records.size)
    }

    @Test
    fun `will not dedup if error is thrown`() {
        var attempt = 0
        val kafka = Mock.withTopology {
            consume(Topics.B)
            .processor(
                DedupProcessor.supplier(
                    serdes = Tables.B.serdes,
                    retention = 10.milliseconds,
                    stateStoreName = "test-dedup-store",
                    { _, value -> value.hashCode() }
                ){
                    if (attempt == 0) {
                        attempt++
                        error("fail")
                    }
                }
            ).produce(Topics.C)
        }
        runCatching { kafka.inputTopic(Topics.B).produce("1", "hello") }
        val store = kafka.getStore(Store("test-dedup-store", Topics.B.serdes))
        assertNull(store.getOrNull("hello".hashCode().toString()))
        kafka.inputTopic(Topics.B).produce("1", "hello")
        assertEquals("hello", store.getOrNull("hello".hashCode().toString()))
        val records = kafka.outputTopic(Topics.C).readRecordsToList()
        assertEquals(1, records.size)
    }
}

