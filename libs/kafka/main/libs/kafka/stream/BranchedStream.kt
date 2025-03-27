package libs.kafka.stream

import libs.kafka.Serdes
import org.apache.kafka.streams.kstream.Branched
import org.apache.kafka.streams.kstream.KStream

class BranchedKStream<K: Any, V : Any> internal constructor(
    private val stream: org.apache.kafka.streams.kstream.BranchedKStream<K, V>,
    private val namedSupplier: () -> String,
) {
    private var nextBranchNumber: Int = 1
        get() = field++

    fun branch(
        predicate: (V) -> Boolean,
        consumed: ConsumedStream<K, V>.() -> Unit,
    ): BranchedKStream<K, V> {
        val namedBranch = "-branch-$nextBranchNumber"
        val internalPredicate = { _:K, value: V -> predicate(value) }
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.branch(internalPredicate, internalBranch)
        return this
    }

    fun default(consumed: ConsumedStream<K, V>.() -> Unit) {
        val namedBranch = "-branch-default"
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.defaultBranch(internalBranch)
    }

    private fun internalBranch(
        branch: (ConsumedStream<K, V>) -> Unit,
        namedBranch: String,
        namedSupplier: () -> String,
    ): Branched<K, V> = Branched.withConsumer(
        { chain: KStream<K, V> -> branch(ConsumedStream(chain, namedSupplier)) },
        namedBranch
    )
}

class BranchedMappedKStream<K: Any, V : Any> internal constructor(
    private val stream: org.apache.kafka.streams.kstream.BranchedKStream<K, V>,
    private val namedSupplier: () -> String,
) {
    private var nextBranchNumber: Int = 1
        get() = field++

    fun branch(
        predicate: (V) -> Boolean,
        consumed: MappedStream<K, V>.() -> Unit,
    ): BranchedMappedKStream<K, V> {
        val namedBranch = "-branch-$nextBranchNumber"
        val internalPredicate = { _:K, value: V -> predicate(value) }
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.branch(internalPredicate, internalBranch)
        return this
    }

    fun default(consumed: MappedStream<K, V>.() -> Unit) {
        val namedBranch = "-branch-default"
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.defaultBranch(internalBranch)
    }

    private fun internalBranch(
        branch: (MappedStream<K, V>) -> Unit,
        namedBranch: String,
        namedSupplier: () -> String,
    ): Branched<K, V> = Branched.withConsumer(
        { chain: KStream<K, V> -> branch(MappedStream(chain, namedSupplier)) },
        namedBranch
    )
}

