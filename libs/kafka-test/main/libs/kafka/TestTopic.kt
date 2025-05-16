package libs.kafka

import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import java.time.Instant

data class TestTopic<K: Any, V: Any>(
    private val input: TestInputTopic<K, V>,
    private val output: TestOutputTopic<K, V>
) {
    fun produce(key: K, value: () -> V) = this.also {
        input.pipeInput(key, value())
    }

    fun produce(key: K, advanceClockMs: Long, value: () -> V) = this.also {
        input.pipeInput(key, value(), Instant.now().plusMillis(advanceClockMs))
    }


    fun tombstone(key: K) = input.pipeInput(key, null)

    fun assertThat() = output.readAndAssert()

    fun readValue(): V = output.readValue()
}
