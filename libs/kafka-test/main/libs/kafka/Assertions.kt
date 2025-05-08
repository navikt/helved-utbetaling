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

    fun hasValueEquals(key: K, index: Int = 0, value: (V) -> V) = this.also {
        val values = valuesForKey(key)
        assertTrue("No values found for key: $key") { values.isNotEmpty() }
        val actual = values.getOrNull(index) ?: fail("No value for key $key on index $index/${values.size - 1} found.")
        assertEquals(value(actual), actual)
    }

    fun hasLastValue(key: K, value: V.() -> Unit) = this.also {
        val last = valuesForKey(key).last()
        value(last)
    }

    fun hasNumberOfRecords(amount: Int) = this.also {
        assertEquals(amount, actuals.size)
    }

    fun hasNumberOfRecordsForKey(key: K, amount: Int) = this.also {
        assertEquals(amount, actuals.filter { it.key == key }.size)
    }

    fun hasKey(key: K) = this.also {
        assertEquals(key, actuals.firstOrNull { it.key == key }?.key)
    }

    fun hasValue(value: V) = this.also {
        assertEquals(value, actuals.firstOrNull()?.value)
    }

    fun isEmpty() = this.also {
        assertTrue(actuals.isEmpty())
    }

    fun isEmptyForKey(key: K) = this.also {
        assertFalse(actuals.any { it.key == key })
    }

    fun hasValueMatching(key: K, index: Int = 0, assertions: (value: V) -> Unit) = this.also {
        val values = valuesForKey(key)
        assertTrue("No values found for key: $key") { values.isNotEmpty() }
        val value = values.getOrNull(index) ?: fail("No value for key $key on index $index/${values.size - 1} found.")
        assertions(value)
    }

    fun withLastValue(assertions: (value: V?) -> Unit) = this.also {
        val value = actuals.last().value ?: fail("No records found.")
        assertions(value)
    }

    fun hasValuesForPredicate(key: K, numberOfValues: Int = 1, predicate: (value: V) -> Boolean) = this.also {
        val values = valuesForKey(key).filter(predicate)
        assertEquals(numberOfValues, values.size, "Should only be $numberOfValues values matching predicate")
    }

    fun containsTombstone(key: K) = this.also {
        assertTrue(valuesForKey(key).contains(null))
    }
}
