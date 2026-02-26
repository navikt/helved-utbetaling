package libs.kafka.processor

import libs.kafka.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import kotlin.test.assertTrue
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
            .processor(StateProcessor(
                supplier = DedupProcessor.supplier(10.milliseconds, Stores.B), 
                named = Named("abc"), 
                storeName = Stores.B.name
            )).produce(Topics.C)
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
            .processor(StateProcessor(
                supplier = DedupProcessor.supplier(10.milliseconds, Stores.B),
                named = Named("abc"),
                storeName = Stores.B.name
            )).produce(Topics.C)
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
            .processor(StateProcessor(
                supplier = DedupProcessor.supplier(10.milliseconds, Stores.B, { _, value -> value.hashCode() }),
                named = Named("abc"),
                storeName = Stores.B.name
            )).produce(Topics.C)
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
            .processor(StateProcessor(
                supplier = DedupProcessor.supplier(10.milliseconds, Stores.B, { key, _ -> key.hashCode() }),
                named = Named("abc"),
                storeName = Stores.B.name
            )).produce(Topics.C)
        }
        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.inputTopic(Topics.B).produce("1", "hello")

        val records = kafka.outputTopic(Topics.C).readRecordsToList()
        assertEquals(1, records.size)
    }

    @Test
    fun `will not dedup if error is thrown`() {
        var attempt = 0

        fun hasher(k: String, v: String): Int = v.hashCode()

        val kafka = Mock.withTopology {
            consume(Topics.B)
            .processor(StateProcessor(
                supplier = DedupProcessor.supplier(10.milliseconds, Stores.Dedup, ::hasher) {
                    if (attempt == 0) {
                        attempt++
                        error("fail")
                    }
                },
                named = Named("abc"),
                storeName = Stores.Dedup.name
            )).produce(Topics.C)
        }
        val store = kafka.getStore(Stores.Dedup)
        runCatching { kafka.inputTopic(Topics.B).produce("1", "hello") }
        assertEquals(1, attempt)
        assertNull(store.getOrNull("1"))
        kafka.inputTopic(Topics.B).produce("1", "hello")
        assertEquals(1, attempt)
        val stateStoreHashedDedupKey = hasher("1", "hello").toString()
        assertEquals("hello", store.getOrNull(stateStoreHashedDedupKey))
        val records = kafka.outputTopic(Topics.C).readRecordsToList()
        assertEquals(1, records.size)
    }
}

