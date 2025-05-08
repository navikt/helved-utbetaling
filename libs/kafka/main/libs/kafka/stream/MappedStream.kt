package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.StateProcessor
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import libs.kafka.processor.LogProduceStateStoreProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

class MappedStream<K: Any, V : Any> internal constructor(
    private val stream: KStream<K, V>,
    private val namedSupplier: () -> String,
) {
    fun produce(topic: Topic<K, V>) {
        val named = "produced-${topic.name}-${namedSupplier()}"
        stream.produceWithLogging(topic, named)
    }

    fun materialize(store: Store<K, V>): KStore<K, V> {
        val loggedStream = stream.addProcessor(LogProduceStateStoreProcessor(store.name))
        return KStore(store, loggedStream.toTable(materialized(store)))
    }

    fun materialize(table: Table<K, V>): KTable<K, V> {
        val loggedStream = stream.addProcessor(LogProduceStateStoreProcessor(table.stateStoreName))
        return KTable(table, loggedStream.toTable(materialized(table)))
    }

    fun <U : Any> map(mapper: (V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues { lr -> mapper(lr) }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <U : Any> map(mapper: (K, V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues { key, value -> mapper(key, value) }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <U : Any> leftJoin(serdes: Serdes<K, V>, right: KTable<K, U>): JoinedStream<K, V, U?> {
        val named = "${namedSupplier()}-left-join-${right.table.sourceTopic.name}"
        val joinedStream = stream.leftJoin(named, serdes, right, ::StreamsPair)
        return JoinedStream(joinedStream, { named })
    }

    fun <U : Any> flatMap(mapper: (V) -> Iterable<U>): MappedStream<K, U> {
        val flattenedStream = stream.flatMapValues { _, value -> mapper(value) }
        return MappedStream(flattenedStream, namedSupplier)
    }

    fun <K2: Any, U : Any> flatMapKeyAndValue(mapper: (K, V) -> Iterable<KeyValue<K2, U>>): MappedStream<K2, U> {
        val fusedStream = stream.flatMap { key, value -> mapper(key, value).map { it.toInternalKeyValue() } }
        return MappedStream(fusedStream, namedSupplier)
    }

    fun <K2: Any> rekey(mapper: (V) -> K2): MappedStream<K2, V> {
        val rekeyedStream = stream.selectKey { _, value -> mapper(value) }
        return MappedStream(rekeyedStream, namedSupplier)
    }

    fun <K2: Any> rekey(mapper: (K, V) -> K2): MappedStream<K2, V> {
        val rekeyedStream = stream.selectKey { key, value -> mapper(key, value) }
        return MappedStream(rekeyedStream, namedSupplier)
    }

    fun filter(lambda: (V) -> Boolean): MappedStream<K, V> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return MappedStream(filteredStream, namedSupplier)
    }

    fun branch(predicate: (V) -> Boolean, consumed: MappedStream<K, V>.() -> Unit): BranchedMappedKStream<K, V> {
        val named = Named.`as`("split-${namedSupplier()}")
        val branchedStream = stream.split(named)
        return BranchedMappedKStream(branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(logger: Log.(V) -> Unit): MappedStream<K, V> {
        val loggedStream = stream.peek { _, value -> logger.invoke(Log.secure, value) }
        return MappedStream(loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(K, V) -> Unit): MappedStream<K, V> {
        val loggedStream = stream.peek { key, value -> log.invoke(Log.secure, key, value) }
        return MappedStream(loggedStream, namedSupplier)
    }

    fun <U : Any> processor(processor: Processor<K, V, U>): MappedStream<K, U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(processedStream, namedSupplier)
    }

    fun <TABLE : Any, U : Any> stateProcessor(processor: StateProcessor<K, TABLE, V, U>): MappedStream<K, U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(processedStream, namedSupplier)
    }

    fun forEach(mapper: (K, V) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach(mapper, named)
    }

    fun forEach(mapper: (V) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach({ _, value -> mapper(value) }, named)
    }
}
