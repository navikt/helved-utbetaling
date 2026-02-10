package libs.kafka.stream

import libs.kafka.*
import libs.kafka.KTable
import libs.kafka.Named
import libs.kafka.processor.*
import org.apache.kafka.streams.kstream.*
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class MappedStream<K: Any, V : Any> internal constructor(
    private val stream: KStream<K, V>,
) {
    fun produce(topic: Topic<K, V>) {
        val logAudStream = stream.process({LogAndAuditProduceTopicProcessor(topic)})
        logAudStream.to(topic.name, topic.produced())
    }

    fun materialize(store: Store<K, V>, materializeWithTrace: Boolean = false): KStore<K, V> {
        val internalKTable = when(materializeWithTrace) {
            false -> stream.process({LogProduceStateStoreProcessor("${store.name}-materialize-log", store.name)}).toTable(Named("${store.name}-materialize").into(), materialized(store))
            true -> stream.toTable(Named("${store.name}-materialize").into(), materializedWithTrace(store))
        }
        return KStore(store, internalKTable)
    }

    fun materialize(table: Table<K, V>): KTable<K, V> {
        val loggedStream = stream.process(
            { LogProduceStateStoreProcessor("${table.stateStoreName}-materialize-log", table.stateStoreName) }
        )
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

    fun <K2: Any, U : Any> mapKeyAndValue(mapper: (K, V) -> KeyValue<K2, U>): MappedStream<K2, U> {
        val mappedStream = stream.map ({ key, value -> mapper(key, value).toInternalKeyValue() })
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

    fun repartition(
        keySerde: StreamSerde<K>,
        valueSerde: StreamSerde<V>,
        named: String,
    ): MappedStream<K, V> {
        val rep = Repartitioned.with(keySerde, valueSerde).withName(Named(named).toString())
        return MappedStream(stream.repartition(rep))
    }

    fun <U : Any> flatMap(mapper: (V) -> Iterable<U>): MappedStream<K, U> {
        val flattenedStream = stream.flatMapValues ({ _, value -> mapper(value) })
        return MappedStream(flattenedStream)
    }

    fun <U : Any> flatMap(mapper: (K, V) -> Iterable<U>): MappedStream<K, U> {
        val flattenedStream = stream.flatMapValues ({ key, value -> mapper(key, value) })
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

    fun filter(lambda: (K, V) -> Boolean): MappedStream<K, V> {
        val filteredStream = stream.filter ({ key, value -> lambda(key, value) })
        return MappedStream(filteredStream)
    }

    fun branch(predicate: (V) -> Boolean, consumed: MappedStream<K, V>.() -> Unit): BranchedMappedStream<K, V> {
        val branchedStream = stream.split()
        return BranchedMappedStream(branchedStream).branch(predicate, consumed)
    }

    fun peek(peek: (K, V, Metadata) -> Unit): MappedStream<K, V> {
        val peeked = stream.process({ PeekMetadataProcessor(peek) })
        return MappedStream(peeked)
    }

    fun <U : Any> processor(processor: Processor<K, V, K, U>): MappedStream<K, U> {
        val processedStream = stream.process(processor.supplier)
        return MappedStream(processedStream)
    }

    fun processor(processor: StateProcessor<K, V, K, V>): MappedStream<K, V> {
        val processedStream = stream.process(processor.supplier, processor.named.into(), processor.storeName)
        return MappedStream(processedStream)
    }

    fun includeHeader(headerKey: String, headerValue: (V) -> String): MappedStream<K, V> {
        val enriched = stream.process( { EnrichHeaderProcessor(headerKey, headerValue)} )
        return MappedStream(enriched)
    }

    fun groupByKey(serdes: Serdes<K, V>, named: String): GroupedStream<K, V> {
        val grouped = stream.groupByKey(Grouped.with(serdes.key, serdes.value).withName(Named(named).toString()))
        return GroupedStream(grouped)
    }

    fun filterNotNull(): MappedStream<K, V> {
        val mappedStream = stream.filterNotNull()
        return MappedStream(mappedStream)
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

    // fun onEach(onEach: (key: K, value: V, metadata: ProcessorMetadata) -> Unit): MappedStream<K, V> {
    //     val peeked = stream
    //         .process({ MetadataProcessor()})
    //         .mapValues ({ _, (kv, metadata) -> 
    //             onEach(kv.key, kv.value, metadata)
    //             kv.value
    //         })
    //     return MappedStream(peeked)
    // }

    fun forEach(mapper: (K, V) -> Unit) {
        stream.foreach(mapper)
    }

    // fun forEach(mapper: (V) -> Unit) {
    //     stream.foreach({ _, value -> mapper(value) })
    // }
}

