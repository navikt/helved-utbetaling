package libs.kafka

import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.test.TestRecord
import java.time.Instant

sealed interface TestTopic<K: Any, V: Any> {
    class Input<K: Any, V: Any>(
        private val input: TestInputTopic<K, V>,
    ): TestTopic<K, V> {
        fun produce(key: K, value: () -> V) = input.pipeInput(key, value())
        fun produce(key: K, advanceClockMs: Long, value: () -> V) = input.pipeInput(key, value(), Instant.now().plusMillis(advanceClockMs))
        fun tombstone(key: K) = input.pipeInput(key, null)
    }

    class Output<K: Any, V: Any>(
        private val output: TestOutputTopic<K, V>,
    ): TestTopic<K, V> {
        fun assertThat() = output.readAndAssert()
        fun readValue(): V = output.readValue()
    }

    class InputOutput<K: Any, V: Any>(
        private val input: TestInputTopic<K, V>,
        private val output: TestOutputTopic<K, V>,
    ): TestTopic<K, V> {
        fun produce(key: K, value: () -> V) = input.pipeInput(key, value())
        fun produce(key: K, advanceClockMs: Long, value: () -> V) = input.pipeInput(key, value(), Instant.now().plusMillis(advanceClockMs))

        fun produce(key: K, headers: Map<String, String>, value: () -> V) {
            val record = TestRecord(key, value())
            headers.forEach { (k, v) -> record.headers().add(k, v.toByteArray(Charsets.UTF_8))}
            input.pipeInput(record)
        }

        fun tombstone(key: K) = input.pipeInput(key, null)
        fun tombstone(key: K, advanceClockMs: Long) = input.pipeInput(key, null, Instant.now().plusMillis(advanceClockMs))
        fun assertThat() = output.readAndAssert()
        fun readValue(): V = output.readValue()
    }
}
