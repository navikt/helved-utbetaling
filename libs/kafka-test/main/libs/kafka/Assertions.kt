package libs.kafka

import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.test.TestRecord
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

fun <K: Any, V : Any> TestOutputTopic<K, V>.readAndAssert() = TopicAssertion.readAndAssertThat(this)

class TopicAssertion<K: Any, V : Any> private constructor(topic: TestOutputTopic<K, V>) {
    companion object {
        fun <K: Any, V : Any> readAndAssertThat(topic: TestOutputTopic<K, V>) = TopicAssertion(topic)
    }

    private val actuals: List<TestRecord<K, V>> = topic.readRecordsToList()
    private fun valuesForKey(key: K) = actuals.filter { it.key == key }.map { it.value }

    fun hasHeader(key: K, header: Pair<String, String>, index: Int = 0) = this.also {
        val record = actuals.getOrNull(index)
        assertNotNull(record)
        val actualHeader = record.headers().singleOrNull { it.key() == header.first }
        assertNotNull(actualHeader)
        assertEquals(header.second, String(actualHeader.value()))
    }

    fun hasHeader(key: K, header: String, index: Int = 0) = this.also {
        val record = actuals.getOrNull(index)
        assertNotNull(record)
        assertNotNull(record.headers().singleOrNull { it.key() == header })
    }

    fun hasTotal(size: Int) = this.also {
        assertEquals(size, actuals.size)
    }

    fun has(key: K, size: Int = 1) = this.also {
        assertEquals(size, actuals.filter { it.key == key }.size)
    }

    fun has(key: K, value: V, index: Int = 0) = this.also {
        assertEquals(value, valuesForKey(key).getOrNull(index))
    }

    fun has(key: K, index: Int = 0, size: Int = 1, value: V) = this.also {
        has(key, size)
        assertEquals(value, valuesForKey(key).getOrNull(index))
    }

    fun with(key: K, index: Int = 0, value: (V) -> Unit) = this.also {
        val actual = valuesForKey(key).getOrNull(index) ?: fail("no record found for key $key")
        value(actual)
    }

    fun hasNot(key: K) = this.also {
        assertFalse(actuals.any { it.key == key })
    }

    fun isEmpty() = this.also {
        assertTrue(actuals.isEmpty())
    }

    fun hasTombstone(key: K) = this.also {
        assertTrue(valuesForKey(key).contains(null))
    }

    fun get(key: K, index: Int = 0): V { 
        val value = valuesForKey(key).getOrNull(index) ?: fail("no record found for key $key")
        return value
    }
}

