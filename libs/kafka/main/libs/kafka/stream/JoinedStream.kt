package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

/**
 * R kan defineres som nullable.
 * Dette er opp til kallstedet for opprettelsen av JoinedKStream.
 * */
@Suppress("UNCHECKED_CAST")
class JoinedStream<K: Any, L : Any, R> internal constructor(
    private val stream: KStream<K, StreamsPair<L, R>>,
    private val namedSupplier: () -> String

) {
    fun <LR : Any> map(mapper: (L, R) -> LR): MappedStream<K, LR> {
        val mappedStream = stream.mapValues { (left, right) -> mapper(left, right) }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <LR : Any> map(mapper: (key: K, L, R) -> LR): MappedStream<K, LR> {
        val mappedStream = stream.mapValues { key, (left, right) -> mapper(key, left, right) }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <K2: Any> rekey(mapper: (L, R) -> K2): JoinedStream<K2, L, R> {
        val rekeyedStream = stream.selectKey { _, (left, right) -> mapper(left, right) }
        return JoinedStream(rekeyedStream, namedSupplier)
    }

    fun <K2: Any, LR : Any> mapKeyValue(mapper: (K, L, R) -> KeyValue<K2, LR>): MappedStream<K2, LR> {
        val mappedStream = stream.map { key, (left, right) -> mapper(key, left, right).toInternalKeyValue() }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <K2: Any, LR : Any> flatMapKeyValue(mapper: (K, L, R) -> Iterable<KeyValue<K2, LR>>): MappedStream<K2, LR> {
        val stream = stream.flatMap { key, (left, right) -> mapper(key, left, right).map { it.toInternalKeyValue() } }
        return MappedStream(stream, namedSupplier)
    }

    fun <LR> mapNotNull(mapper: (L, R) -> LR): MappedStream<K, LR & Any> {
        val mappedStream = stream.mapValues { _, (left, right) -> mapper(left, right) }.filterNotNull()
        return MappedStream(mappedStream, namedSupplier)
    }

    fun filter(lambda: (StreamsPair<L, R>) -> Boolean): JoinedStream<K, L, R> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return JoinedStream(filteredStream, namedSupplier)
    }

    fun branch(
        predicate: (StreamsPair<L, R>) -> Boolean,
        consumed: MappedStream<K, StreamsPair<L, R>>.() -> Unit,
    ): BranchedMappedKStream<K, StreamsPair<L, R>> {
        val branchedStream = stream.split(Named.`as`("split-${namedSupplier()}"))
        return BranchedMappedKStream(branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(log: Log.(L, R) -> Unit): JoinedStream<K, L, R> {
        val loggedStream = stream.peek { _, (left, right) -> log.invoke(Log.secure, left, right) }
        return JoinedStream(loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(K, L, R) -> Unit): JoinedStream<K, L, R> {
        val loggedStream = stream.peek { key, (left, right) -> log.invoke(Log.secure, key, left, right) }
        return JoinedStream(loggedStream, namedSupplier)
    }

    fun <LR : Any> processor(processor: Processor<K, StreamsPair<L, R>, LR>): MappedStream<K, LR> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(processorStream, namedSupplier)
    }

    fun processor(processor: Processor<K, StreamsPair<L, R>, StreamsPair<L, R>>): JoinedStream<K, L, R> {
        val processorStream = stream.addProcessor(processor)
        return JoinedStream(processorStream, namedSupplier)
    }
}
