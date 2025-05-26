package libs.kafka.stream

import kotlin.time.Duration
import kotlin.time.toJavaDuration
import libs.kafka.*
import libs.kafka.processor.*
import libs.kafka.processor.LogProduceStateStoreProcessor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.SessionWindows
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.SessionWindowedKStream
import org.apache.kafka.streams.kstream.EmitStrategy
import org.apache.kafka.streams.kstream.internals.emitstrategy.WindowCloseStrategy
import org.apache.kafka.streams.kstream.Windowed

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

    fun <U: Any> leftJoin(
        key: StreamSerde<K>,
        value: StreamSerde<V>,
        right: KTable<K, U>
    ): JoinedStream<K, V, U?> {
        val named = "${namedSupplier()}-left-join-${right.table.sourceTopic.name}"
        val joinedStream = stream.leftJoin(named, Serdes(key, value), right, ::StreamsPair)
        return JoinedStream(joinedStream, { named })
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

    fun groupByKey(serdes: Serdes<K, V>): GroupedStream<K, V> {
        val grouped = stream.groupByKey(Grouped.with(serdes.key, serdes.value))
        return GroupedStream(grouped, { "${namedSupplier()}-groupByKey" })
    }

    /**
     * Creates a new window after [inactivityGap] duration.
     *  |||||||||
     *               ||||||||
     *                           |||||||||||||
     */
    fun sessionWindow(
        keySerde: StreamSerde<K>,
        valueSerde: StreamSerde<V>,
        inactivityGap: Duration,
    ): SessionWindowedStream<K, V> {
        val window = SessionWindows.ofInactivityGapWithNoGrace(inactivityGap.toJavaDuration())
        val groupSerde = Grouped.with(keySerde, valueSerde)
        val windowedStream: SessionWindowedKStream<K, V> = stream.groupByKey(groupSerde).windowedBy(window)
        return SessionWindowedStream(Serdes(keySerde, valueSerde), windowedStream, {"${namedSupplier()}-session-window"})
    }

    fun onEach(onEach: (key: K, value: V, metadata: ProcessorMetadata) -> Unit): MappedStream<K, V> {
        val peeked = stream
            .addProcessor(MetadataProcessor("${namedSupplier()}-onEach"))
            .mapValues { _, (kv, metadata) -> 
                onEach(kv.key, kv.value, metadata)
                kv.value
            }
        return MappedStream(peeked, namedSupplier)
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
