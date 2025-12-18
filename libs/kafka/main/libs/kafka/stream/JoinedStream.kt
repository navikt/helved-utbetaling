@file:Suppress("UNCHECKED_CAST")

package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.PeekMetadataProcessor
import libs.kafka.processor.Processor
import org.apache.kafka.streams.kstream.KStream

/**
 * R kan defineres som nullable.
 * Dette er opp til kallstedet for opprettelsen av JoinedKStream.
 * */
class JoinedStream<K: Any, L : Any, R> internal constructor(
    private val stream: KStream<K, StreamsPair<L, R>>,

) {
    fun <LR : Any> map(mapper: (L, R) -> LR): MappedStream<K, LR> {
        val mappedStream = stream.mapValues ({ (left, right) -> mapper(left, right) })
        return MappedStream(mappedStream)
    }

    fun <LR : Any> map(mapper: (K, L, R) -> LR): MappedStream<K, LR> {
        val mappedStream = stream.mapValues ({ key, (left, right) -> mapper(key, left, right) })
        return MappedStream(mappedStream)
    }

    fun <K2: Any> rekey(mapper: (L, R) -> K2): JoinedStream<K2, L, R> {
        val rekeyedStream = stream.selectKey ({ _, (left, right) -> mapper(left, right) })
        return JoinedStream(rekeyedStream)
    }

    fun <K2: Any, LR : Any> mapKeyValue(mapper: (K, L, R) -> KeyValue<K2, LR>): MappedStream<K2, LR> {
        val mappedStream = stream.map ({ key, (left, right) -> mapper(key, left, right).toInternalKeyValue() })
        return MappedStream(mappedStream)
    }

    fun <K2: Any, U : Any> flatMapKeyValue(mapper: (K, L, R) -> Iterable<KeyValue<K2, U>>): MappedStream<K2, U> {
        val stream = stream.flatMap ({ key, (left, right) -> mapper(key, left, right).map { it.toInternalKeyValue() } })
        return MappedStream(stream)
    }

    fun <LR> mapNotNull(mapper: (L, R) -> LR): MappedStream<K, LR & Any> {
        val mappedStream = stream.mapValues ({ _, (left, right) -> mapper(left, right) }).filterNotNull()
        return MappedStream(mappedStream)
    }

    fun filter(lambda: (StreamsPair<L, R>) -> Boolean): JoinedStream<K, L, R> {
        val filteredStream = stream.filter ({ _, value -> lambda(value) })
        return JoinedStream(filteredStream )
    }

    fun branch(
        predicate: (StreamsPair<L, R>) -> Boolean,
        consumed: MappedStream<K, StreamsPair<L, R>>.() -> Unit,
    ): BranchedMappedStream<K, StreamsPair<L, R>> {
        val branchedStream = stream.split()
        return BranchedMappedStream(branchedStream).branch(predicate, consumed)
    }

    fun peek(peek: (K, L, R) -> Unit): JoinedStream<K, L, R> {
        val peekedStream = stream.peek ({ key, (left, right) -> peek(key, left, right) })
        return JoinedStream(peekedStream)
    }

    fun <LR : Any> processor(processor: Processor<K, StreamsPair<L, R>, K, LR>): MappedStream<K, LR> {
        val processorStream = stream.process(processor.supplier)
        return MappedStream(processorStream)
    }

    fun processor(processor: Processor<K, StreamsPair<L, R>, K, StreamsPair<L, R>>): JoinedStream<K, L, R> {
        val processorStream = stream.process(processor.supplier)
        return JoinedStream(processorStream )
    }
}
