package libs.kafka

import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import java.time.Instant

// data class TestTopic<K: Any, V: Any>(
//     private val input: TestInputTopic<K, V>,
//     private val output: TestOutputTopic<K, V>
// ) {
//     fun produce(key: K, value: () -> V) = this.also {
//         input.pipeInput(key, value())
//     }
//
//     fun produce(key: K, advanceClockMs: Long, value: () -> V) = this.also {
//         input.pipeInput(key, value(), Instant.now().plusMillis(advanceClockMs))
//     }
//
//     fun tombstone(key: K) = input.pipeInput(key, null)
//
//     fun assertThat() = output.readAndAssert()
//
//     fun readValue(): V = output.readValue()
// }

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
        fun tombstone(key: K) = input.pipeInput(key, null)
        fun assertThat() = output.readAndAssert()
        fun readValue(): V = output.readValue()
    }
}
