package libs.kafka.stream

import org.apache.kafka.streams.kstream.Branched
import org.apache.kafka.streams.kstream.BranchedKStream
import org.apache.kafka.streams.kstream.KStream

class BranchedStream<K: Any, V : Any> internal constructor(
    private val stream: BranchedKStream<K, V>,
) {
    fun branch(
        predicate: (V) -> Boolean,
        consumed: ConsumedStream<K, V>.() -> Unit,
    ): BranchedStream<K, V> {
        val branch = Branched.withConsumer({ consumed(ConsumedStream(it))})
        stream.branch({ _, value: V -> predicate(value) }, branch)
        return this
    }

    fun default(consumed: ConsumedStream<K, V>.() -> Unit) {
        val branch = Branched.withConsumer({ consumed(ConsumedStream(it))})
        stream.defaultBranch(branch)
    }
}

class BranchedMappedStream<K: Any, V : Any> internal constructor(
    private val stream: BranchedKStream<K, V>,
) {
    fun branch(
        predicate: (V) -> Boolean,
        consumed: MappedStream<K, V>.() -> Unit,
    ): BranchedMappedStream<K, V> {
        val branch = Branched.withConsumer({ consumed(MappedStream(it)) })
        stream.branch({ _, value: V -> predicate(value) }, branch)
        return this
    }

    fun default(consumed: MappedStream<K, V>.() -> Unit) {
        val branch = Branched.withConsumer({ chain: KStream<K, V> -> consumed(MappedStream(chain)) })
        stream.defaultBranch(branch)
    }
}

