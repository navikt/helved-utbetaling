package libs.kafka.stream

import libs.kafka.*
import libs.kafka.KTable
import libs.kafka.Named
import libs.kafka.processor.EnrichHeaderProcessor
import libs.kafka.processor.LogAndAuditProduceTopicProcessor
import libs.kafka.processor.Processor
import libs.kafka.processor.StateProcessor
import org.apache.kafka.streams.kstream.*
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class ConsumedStream<K: Any, V : Any> internal constructor(
    private val stream: KStream<K, V>,
) {
    fun produce(topic: Topic<K, V>) {
        val logAudStream = stream.process({LogAndAuditProduceTopicProcessor(topic)})
        logAudStream.to(topic.name, topic.produced())
    }

    fun produce(topic: Topic<K, V>, id: Int) {
        val logAudStream = stream.process({LogAndAuditProduceTopicProcessor(topic)})
        logAudStream.to(topic.name, topic.produced())
    }

    fun <K2: Any> rekey(selectKeyFromValue: (V) -> K2): ConsumedStream<K2, V> {
        val rekeyedStream = stream.selectKey ({ _, value -> selectKeyFromValue(value) })
        return ConsumedStream(rekeyedStream)
    }

    fun <K2: Any> rekey(selectKeyFromValue: (K, V) -> K2): ConsumedStream<K2, V> {
        val rekeyedStream = stream.selectKey ({ key, value -> selectKeyFromValue(key, value) })
        return ConsumedStream(rekeyedStream)
    }

    fun filter(lambda: (V) -> Boolean): ConsumedStream<K, V> {
        return ConsumedStream(stream.filter ({ _, value -> lambda(value) }))
    }

    fun filterKey(lambda: (K) -> Boolean): ConsumedStream<K, V> {
        val filteredStream = stream.filter ({ key, _ -> lambda(key) })
        return ConsumedStream(filteredStream)
    }

    fun <U : Any> map(mapper: (V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues ({ value -> mapper(value) })
        return MappedStream(mappedStream)
    }

    fun <U : Any> map(mapper: (K, V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues(mapper)
        return MappedStream(mappedStream)
    }

    fun merge(other: ConsumedStream<K, V>): ConsumedStream<K, V> {
        return ConsumedStream(stream.merge(other.stream))
    }

    // fun <U : Any> mapWithMetadata(mapper: (V, ProcessorMetadata) -> U): MappedStream<K, U> {
    //     val mappedStream = stream
    //         .process({MetadataProcessor()})
    //         .mapValues ({ (kv, metadata) -> mapper(kv.value, metadata) })
    //     return MappedStream(mappedStream)
    // }

    fun <U> mapNotNull(mapper: (K, V) -> U): MappedStream<K, U & Any> {
        val valuedStream = stream
            .mapValues ({ key, value -> mapper(key, value) })
            .filterNotNull()
        return MappedStream(valuedStream)
    }

    fun <U : Any> flatMap(mapper: (K, V) -> Iterable<U>): MappedStream<K, U> {
        val fusedStream = stream.flatMapValues ({ key, value -> mapper(key, value) })
        return MappedStream(fusedStream)
    }

    fun <K2: Any, U : Any> flatMapKeyAndValue(mapper: (K, V) -> Iterable<KeyValue<K2, U>>): MappedStream<K2, U> {
        val fusedStream = stream.flatMap ({ key, value -> mapper(key, value).map { it.toInternalKeyValue() } })
        return MappedStream(fusedStream)
    }

    fun <K2: Any, U : Any> mapKeyAndValue(mapper: (K, V) -> Pair<K2, U>): MappedStream<K2, U> {
        val fusedStream = stream.map ({ key, value -> mapper(key, value).toInternalKeyValue() })
        return MappedStream(fusedStream)
    }

    fun groupByKey(key: StreamSerde<K>, value: StreamSerde<V>, named: String): GroupedStream<K, V> {
        val grouped = stream.groupByKey(Grouped.with(key, value).withName(Named(named).toString()))
        return GroupedStream(grouped)
    }

    fun groupByKey(serdes: Serdes<K, V>, named: String): GroupedStream<K, V> {
        val grouped = stream.groupByKey(Grouped.with(serdes.key, serdes.value).withName(Named(named).toString()))
        return GroupedStream(grouped)
    }

    /**
     * Window will change when something exceeds the window frame or when something new comes in.
     * @param windowSize the size of the window
     * |      <- new record
     * ||     <- new record
     *  |     <- first record exceeded
     *  ||    <- new record
     */
    fun slidingWindow(serdes: Serdes<K, V>, windowSize: Duration, named: String): TimeWindowedStream<K, V> {
        /*
         * TODO: skal noen av vinduene ha gracePeriod?
         * Dvs hvor lenge skal streamen vente på at en melding har et timestamp som passer inn i vinduet.  timestamp enn "nå".
         * Dette vil ta noen out-of-order records som oppstår f.eks dersom klokkene til producerne er ulike
         */
        val sliding = SlidingWindows.ofTimeDifferenceWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(serdes.key, serdes.value).withName(Named(named).toString())
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(sliding)
        return TimeWindowedStream(serdes, windowedStream)
    }

    /**
     * Window size and advance size will overlap some.
     * @param advanceSize must be less than [windowSize]
     *  |||||||||||||
     *             |||||||||||||
     *                        |||||||||||||
     */
    fun hoppingWindow(serdes: Serdes<K, V>, windowSize: Duration, advanceSize: Duration, named: String): TimeWindowedStream<K, V> {
        val window = TimeWindows
            .ofSizeWithNoGrace(windowSize.toJavaDuration())
            .advanceBy(advanceSize.toJavaDuration())

        val groupSerde = Grouped.with(serdes.key, serdes.value).withName(Named(named).toString())
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(serdes, windowedStream)
    }

    /**
     * Tumbling window is a hopping window, but where window size and advance size is equal.
     * This results in no overlaps or duplicates.
     *  |||||||||||||
     *               |||||||||||||
     *                            |||||||||||||
     */
    fun tumblingWindow(serdes: Serdes<K, V>, windowSize: Duration, named: String): TimeWindowedStream<K, V> {
        val window = TimeWindows.ofSizeWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(serdes.key, serdes.value).withName(Named(named).toString())
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(serdes, windowedStream)
    }

    /**
     * Creates a new window after [inactivityGap] duration.
     *  |||||||||
     *               ||||||||
     *                           |||||||||||||
     */
    // fun sessionWindow(serdes: Serdes<K, V>, inactivityGap: Duration): SessionWindowedStream<K, V> {
    //     val window = SessionWindows.ofInactivityGapWithNoGrace(inactivityGap.toJavaDuration())
    //     val groupSerde = Grouped.with(serdes.key, serdes.value)
    //     val windowedStream: SessionWindowedKStream<K, V> = stream.groupByKey(groupSerde).windowedBy(window)
    //     return SessionWindowedStream(serdes, windowedStream, namedSupplier)
    // }

    fun <R : Any> join(left: Topic<K, V>, right: KTable<K, R>): JoinedStream<K, V, R> {
        val joinedStream = stream.join(left, right, ::StreamsPair)
        return JoinedStream(joinedStream)
    }

    fun <R : Any> leftJoin(left: Topic<K, V>, right: KTable<K, R>): JoinedStream<K, V, R?> {
        val joinedStream = stream.leftJoin(left, right, ::StreamsPair)
        return JoinedStream(joinedStream)
    }

    fun branch(predicate: (V) -> Boolean, consumed: ConsumedStream<K, V>.() -> Unit): BranchedStream<K, V> {
        val branched = stream.split()
        return BranchedStream(branched).branch(predicate, consumed)
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

    fun repartition(
        topic: Topic<K, V>,
        partitions: Int,
        named: String = topic.name,
    ): ConsumedStream<K, V> {
        val repartition = Repartitioned
            .with(topic.serdes.key, topic.serdes.value)
            .withNumberOfPartitions(partitions)
            .withName(Named(named).toString())
        return ConsumedStream(stream.repartition(repartition))
    }

    fun <U : Any> processor(processor: Processor<K, V, K, U>): MappedStream<K, U> {
        val processorStream = stream.process(processor.supplier)
        return MappedStream(processorStream)
    }

    fun processor(processor: StateProcessor<K, V, K, V>): MappedStream<K, V> {
        val processedStream = stream.process(processor.supplier, processor.named.into(), processor.storeName)
        return MappedStream(processedStream)
    }

    fun includeHeader(headerKey: String, headerValue: (V) -> String): ConsumedStream<K, V> {
        val enriched = stream.process( { EnrichHeaderProcessor(headerKey, headerValue)} )
        return ConsumedStream(enriched)
    }

    fun forEach(mapper: (K, V) -> Unit) {
        stream.foreach(mapper)
    }
}

