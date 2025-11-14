package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.*
import libs.kafka.processor.Processor.Companion.addProcessor
// import libs.kafka.processor.StateProcessor.Companion.addProcessor
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.SessionWindowedKStream
import org.apache.kafka.streams.kstream.SessionWindows
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class MappedStream<K: Any, V : Any> internal constructor(
    private val stream: KStream<K, V>,
) {
    fun produce(topic: Topic<K, V>) {
        stream.produceWithLogging(topic)
    }

    fun materialize(store: Store<K, V>): KStore<K, V> {
        val loggedStream = stream.addProcessor(LogProduceStateStoreProcessor("${store.name}-materialize-log", store.name))
        return KStore(store, loggedStream.toTable(Named("${store.name}-materialize").into(), materialized(store)))
    }

    fun materialize(table: Table<K, V>): KTable<K, V> {
        val loggedStream = stream.addProcessor(LogProduceStateStoreProcessor("${table.stateStoreName}-materialize-log", table.stateStoreName))
        return KTable(table, loggedStream.toTable(Named("${table.stateStoreName}-materialize").into(), materialized(table)))
    }

    fun <U : Any> map(mapper: (V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues ({ lr -> mapper(lr) })
        return MappedStream(mappedStream)
    }

    fun <U : Any> map(mapper: (K, V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues ({ key, value -> mapper(key, value) })
        return MappedStream(mappedStream)
    }

    fun <U: Any> leftJoin(
        key: StreamSerde<K>,
        value: StreamSerde<V>,
        right: KTable<K, U>,
        named: String,
    ): JoinedStream<K, V, U?> {
        val joinedStream = stream.leftJoin(named, Serdes(key, value), right, ::StreamsPair)
        return JoinedStream(joinedStream)
    }

    fun <U : Any> leftJoin(
        serdes: Serdes<K, V>,
        right: KTable<K, U>,
        named: String,
    ): JoinedStream<K, V, U?> {
        val joinedStream = stream.leftJoin(named, serdes, right, ::StreamsPair)
        return JoinedStream(joinedStream)
    }

    fun <U : Any> flatMap(mapper: (V) -> Iterable<U>): MappedStream<K, U> {
        val flattenedStream = stream.flatMapValues ({ _, value -> mapper(value) })
        return MappedStream(flattenedStream)
    }

    fun <K2: Any, U : Any> flatMapKeyAndValue(mapper: (K, V) -> Iterable<KeyValue<K2, U>>): MappedStream<K2, U> {
        val fusedStream = stream.flatMap ({ key, value -> mapper(key, value).map { it.toInternalKeyValue() } })
        return MappedStream(fusedStream)
    }

    fun <K2: Any> rekey(mapper: (V) -> K2): MappedStream<K2, V> {
        val rekeyedStream = stream.selectKey ({ _, value -> mapper(value) })
        return MappedStream(rekeyedStream)
    }

    fun <K2: Any> rekey(mapper: (K, V) -> K2): MappedStream<K2, V> {
        val rekeyedStream = stream.selectKey ({ key, value -> mapper(key, value) })
        return MappedStream(rekeyedStream)
    }

    fun filter(lambda: (V) -> Boolean): MappedStream<K, V> {
        val filteredStream = stream.filter ({ _, value -> lambda(value) })
        return MappedStream(filteredStream)
    }

    fun branch(predicate: (V) -> Boolean, consumed: MappedStream<K, V>.() -> Unit): BranchedMappedStream<K, V> {
        val branchedStream = stream.split()
        return BranchedMappedStream(branchedStream).branch(predicate, consumed)
    }

    fun secureLog(logger: Log.(V) -> Unit): MappedStream<K, V> {
        val loggedStream = stream.peek ({ _, value -> logger.invoke(Log.secure, value) })
        return MappedStream(loggedStream)
    }

    fun secureLogWithKey(log: Log.(K, V) -> Unit): MappedStream<K, V> {
        val loggedStream = stream.peek ({ key, value -> log.invoke(Log.secure, key, value) })
        return MappedStream(loggedStream)
    }

    fun <U : Any> processor(processor: Processor<K, V, U>): MappedStream<K, U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(processedStream)
    }

    fun processor(processor: StateProcessor<K, V, K, V>): MappedStream<K, V> {
        val processedStream = stream.process(processor.supplier, processor.named.into(), processor.storeName)
        return MappedStream(processedStream)
    }

    fun groupByKey(serdes: Serdes<K, V>, named: String): GroupedStream<K, V> {
        val grouped = stream.groupByKey(Grouped.with(serdes.key, serdes.value).withName(Named(named).toString()))
        return GroupedStream(grouped)
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
        named: String,
    ): SessionWindowedStream<K, V> {
        val window = SessionWindows.ofInactivityGapWithNoGrace(inactivityGap.toJavaDuration())
        val groupSerde = Grouped.with(keySerde, valueSerde).withName(Named(named).toString())
        val windowedStream: SessionWindowedKStream<K, V> = stream.groupByKey(groupSerde).windowedBy(window)
        return SessionWindowedStream(Serdes(keySerde, valueSerde), windowedStream)
    }

    fun onEach(onEach: (key: K, value: V, metadata: ProcessorMetadata) -> Unit): MappedStream<K, V> {
        val peeked = stream
            .addProcessor(MetadataProcessor())
            .mapValues ({ _, (kv, metadata) -> 
                onEach(kv.key, kv.value, metadata)
                kv.value
            })
        return MappedStream(peeked)
    }

    fun forEach(mapper: (K, V) -> Unit) {
        stream.foreach(mapper)
    }

    fun forEach(mapper: (V) -> Unit) {
        stream.foreach({ _, value -> mapper(value) })
    }
}

