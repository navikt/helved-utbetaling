package libs.kafka

import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.TestOutputTopic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

fun <K: Any, V : Any> TestOutputTopic<K, V>.readAndAssert() = TopicAssertion.readAndAssertThat(this)

class TopicAssertion<K: Any, V : Any> private constructor(topic: TestOutputTopic<K, V>) {
    companion object {
        fun <K: Any, V : Any> readAndAssertThat(topic: TestOutputTopic<K, V>) = TopicAssertion(topic)
    }

    private val actuals: List<KeyValue<K, V>> = topic.readKeyValuesToList()
    private fun valuesForKey(key: K) = actuals.filter { it.key == key }.map { it.value }

    fun hasTotal(size: Int) = this.also {
        assertEquals(size, actuals.size)
    }

    fun has(key: K, size: Int = 1) = this.also {
        assertEquals(size, actuals.filter { it.key == key }.size)
    }

    fun has(key: K, value: V, index: Int = 0) = this.also {
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
}
