package libs.kafka.stream

import kotlin.time.Duration
import kotlin.time.toJavaDuration
import libs.kafka.*
import libs.kafka.KTable
import libs.kafka.filterNotNull
import libs.kafka.processor.MetadataProcessor
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.ProcessorMetadata
import libs.kafka.processor.StateProcessor
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import libs.kafka.produceWithLogging
import org.apache.kafka.streams.kstream.*

class ConsumedStream<K: Any, V : Any> internal constructor(
    private val stream: KStream<K, V>,
    private val namedSupplier: () -> String,
) {
    fun produce(topic: Topic<K, V>) {
        val named = "produced-${topic.name}-${namedSupplier()}"
        stream.produceWithLogging(topic, named)
    }

    fun <K2: Any> rekey(selectKeyFromValue: (V) -> K2): ConsumedStream<K2, V> {
        val rekeyedStream = stream.selectKey { _, value -> selectKeyFromValue(value) }
        return ConsumedStream(rekeyedStream, namedSupplier)
    }

    fun <K2: Any> rekey(selectKeyFromValue: (K, V) -> K2): ConsumedStream<K2, V> {
        val rekeyedStream = stream.selectKey { key, value -> selectKeyFromValue(key, value) }
        return ConsumedStream(rekeyedStream, namedSupplier)
    }

    fun filter(lambda: (V) -> Boolean): ConsumedStream<K, V> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return ConsumedStream(filteredStream, namedSupplier)
    }

    fun filterKey(lambda: (K) -> Boolean): ConsumedStream<K, V> {
        val filteredStream = stream.filter { key, _ -> lambda(key) }
        return ConsumedStream(filteredStream, namedSupplier)
    }

    fun <U : Any> map(mapper: (V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues { value -> mapper(value) }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <U : Any> map(mapper: (K, V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues { key, value -> mapper(key, value) }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <U : Any> mapWithMetadata(mapper: (V, ProcessorMetadata) -> U): MappedStream<K, U> {
        val mappedStream = stream.addProcessor(MetadataProcessor(namedSupplier())).mapValues { (kv, metadata) -> mapper(kv.value, metadata) }
        return MappedStream(mappedStream, namedSupplier)
    }

    fun <U> mapNotNull(mapper: (K, V) -> U): MappedStream<K, U & Any> {
        val valuedStream = stream.mapValues { key, value -> mapper(key, value) }.filterNotNull()
        return MappedStream(valuedStream, namedSupplier)
    }

    fun <U : Any> flatMap(mapper: (K, V) -> Iterable<U>): MappedStream<K, U> {
        val fusedStream = stream.flatMapValues { key, value -> mapper(key, value) }
        return MappedStream(fusedStream, namedSupplier)
    }

    fun <K2: Any, U : Any> flatMapKeyAndValue(mapper: (K, V) -> Iterable<KeyValue<K2, U>>): MappedStream<K2, U> {
        val fusedStream = stream.flatMap { key, value -> mapper(key, value).map { it.toInternalKeyValue() } }
        return MappedStream(fusedStream, namedSupplier)
    }

    fun <K2: Any, U : Any> mapKeyAndValue(mapper: (K, V) -> Pair<K2, U>): MappedStream<K2, U> {
        val fusedStream = stream.map { key, value -> mapper(key, value).toInternalKeyValue() }
        return MappedStream(fusedStream, namedSupplier)
    }

    fun groupByKey(
        key: StreamSerde<K>,
        value: StreamSerde<V>,
    ): GroupedStream<K, V> {
        val grouped = stream.groupByKey(Grouped.with(key, value))
        return GroupedStream(grouped, { "${namedSupplier()}-groupByKey" })
    }

    fun groupByKey(serdes: Serdes<K, V>): GroupedStream<K, V> {
        val grouped = stream.groupByKey(Grouped.with(serdes.key, serdes.value))
        return GroupedStream(grouped, { "${namedSupplier()}-groupByKey" })
    }

    /**
     * Window will change when something exceeds the window frame or when something new comes in.
     * @param windowSize the size of the window
     * |      <- new record
     * ||     <- new record
     *  |     <- first record exceeded
     *  ||    <- new record
     */
    fun slidingWindow(serdes: Serdes<K, V>, windowSize: Duration): TimeWindowedStream<K, V> {
        /*
         * TODO: skal noen av vinduene ha gracePeriod?
         * Dvs hvor lenge skal streamen vente på at en melding har et timestamp som passer inn i vinduet.  timestamp enn "nå".
         * Dette vil ta noen out-of-order records som oppstår f.eks dersom klokkene til producerne er ulike
         */
        val sliding = SlidingWindows.ofTimeDifferenceWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(serdes.key, serdes.value)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(sliding)
        return TimeWindowedStream(serdes, windowedStream, namedSupplier)
    }

    /**
     * Window size and advance size will overlap some.
     * @param advanceSize must be less than [windowSize]
     *  |||||||||||||
     *             |||||||||||||
     *                        |||||||||||||
     */
    fun hoppingWindow(serdes: Serdes<K, V>, windowSize: Duration, advanceSize: Duration): TimeWindowedStream<K, V> {
        val window = TimeWindows
            .ofSizeWithNoGrace(windowSize.toJavaDuration())
            .advanceBy(advanceSize.toJavaDuration())

        val groupSerde = Grouped.with(serdes.key, serdes.value)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(serdes, windowedStream, namedSupplier)
    }

    /**
     * Tumbling window is a hopping window, but where window size and advance size is equal.
     * This results in no overlaps or duplicates.
     *  |||||||||||||
     *               |||||||||||||
     *                            |||||||||||||
     */
    fun tumblingWindow(serdes: Serdes<K, V>, windowSize: Duration): TimeWindowedStream<K, V> {
        val window = TimeWindows.ofSizeWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(serdes.key, serdes.value)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(serdes, windowedStream, namedSupplier)
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
        val named = { "${left.name}-join-${right.table.sourceTopic.name}" }
        return JoinedStream(joinedStream, named)
    }

    fun <R : Any> leftJoin(left: Topic<K, V>, right: KTable<K, R>): JoinedStream<K, V, R?> {
        val named = { "${left.name}-left-join-${right.table.sourceTopic.name}" }
        val joinedStream = stream.leftJoin(left, right, ::StreamsPair)
        return JoinedStream(joinedStream, named)
    }

    fun branch(predicate: (V) -> Boolean, consumed: ConsumedStream<K, V>.() -> Unit): BranchedKStream<K, V> {
        val splittedStream = stream.split(Named.`as`("split-${namedSupplier()}"))
        return BranchedKStream(splittedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(log: Log.(V) -> Unit): ConsumedStream<K, V> {
        val loggedStream = stream.peek { _, value -> log.invoke(Log.secure, value) }
        return ConsumedStream(loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(K, V) -> Unit): ConsumedStream<K, V> {
        val loggedStream = stream.peek { key, value -> log.invoke(Log.secure, key, value) }
        return ConsumedStream(loggedStream, namedSupplier)
    }

    fun repartition(serdes: Serdes<K, V>, partitions: Int): ConsumedStream<K, V> {
        val repartition = Repartitioned
            .with(serdes.key, serdes.value)
            .withNumberOfPartitions(partitions)
            .withName(namedSupplier())
        return ConsumedStream(stream.repartition(repartition), namedSupplier)
    }

    fun <U : Any> processor(processor: Processor<K, V, U>): MappedStream<K, U> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(processorStream, namedSupplier)
    }

    fun <TABLE : Any, U : Any> processor(processor: StateProcessor<K, TABLE, V, U>): MappedStream<K, U> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(processorStream, namedSupplier)
    }

    fun forEach(mapper: (K, V) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach(mapper, named)
    }
}
