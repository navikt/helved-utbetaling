package libs.kafka

import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic

data class TestTopic<K: Any, V: Any>(
    private val input: TestInputTopic<K, V>,
    private val output: TestOutputTopic<K, V>
) {
    fun produce(key: K, value: () -> V) = this.also {
        input.pipeInput(key, value())
    }

    fun tombstone(key: K) = input.pipeInput(key, null)

    fun assertThat() = output.readAndAssert()

    fun readValue(): V = output.readValue()
}
