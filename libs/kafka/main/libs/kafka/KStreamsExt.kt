package libs.kafka

import libs.kafka.processor.*
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.*

internal fun <K: Any, V : Any> KStream<K, V>.produceWithLogging(
    topic: Topic<K, V>,
    named: String,
) {
    val logger = LogProduceTopicProcessor("log-${named}", topic)
    val options = topic.produced(named)
    return addProcessor(logger).to(topic.name, options)
}

internal fun <K: Any, L : Any, R : Any, LR> KStream<K, L>.leftJoin(
    left: Topic<K, L>,
    right: KTable<K, R>,
    joiner: (K, L, R?) -> LR,
): KStream<K, LR> {
    val ktable = right.internalKTable
    val joined = left leftJoin right
    return leftJoin(ktable, joiner, joined)
}

internal fun <K: Any, L : Any, R : Any, LR> KStream<K, L>.leftJoin(
    named: String,
    leftSerdes: Serdes<K, L>,
    ktable: KTable<K, R>,
    joiner: (L, R?) -> LR,
): KStream<K, LR> {
    val joined = Joined.with(leftSerdes.key, leftSerdes.value, ktable.table.serdes.value, named)
    return leftJoin(ktable.internalKTable, joiner, joined)
}

internal fun <K: Any, L : Any, R : Any, LR> KStream<K, L>.leftJoin(
    left: Topic<K, L>,
    right: KTable<K, R>,
    joiner: (L, R?) -> LR,
): KStream<K, LR> {
    val ktable = right.internalKTable
    val joined = left leftJoin right
    return leftJoin(ktable, joiner, joined)
}

internal fun <K: Any, L : Any, R : Any, LR> KTable<K, L>.leftJoin(
    right: KTable<K, R>,
    joiner: (L?, R?) -> LR,
): KStream<K, LR> {
    return internalKTable.leftJoin(right.internalKTable, joiner).toStream()
}

internal fun <K: Any, L : Any, R : Any, LR> KStream<K, L>.join(
    left: Topic<K, L>,
    right: KTable<K, R>,
    joiner: (K, L, R) -> LR,
): KStream<K, LR> {
    val ktable = right.tombstonedInternalKTable
    val joined = left join right
    return join(ktable, joiner, joined)
}

internal fun <K: Any, L : Any, R : Any, LR> KStream<K, L>.join(
    left: Topic<K, L>,
    right: KTable<K, R>,
    joiner: (L, R) -> LR,
): KStream<K, LR> {
    val ktable = right.tombstonedInternalKTable
    val joined = left join right
    return join(ktable, joiner, joined)
}

internal fun <K: Any, V : Any> KStream<K, V?>.toKTable(table: Table<K, V>): KTable<K, V> {
    val internalKTable = addProcessor(LogProduceTableProcessor(table))
        .toTable(
            Named.`as`("${table.sourceTopicName}-to-table"),
            materialized(table)
        )

    return KTable(table, internalKTable)
}

internal fun <K: Any, V> repartitioned(table: Table<K, V & Any>, partitions: Int): Repartitioned<K, V> {
    return Repartitioned
        .with(table.sourceTopic.serdes.key, table.sourceTopic.serdes.value)
        .withNumberOfPartitions(partitions)
        .withName(table.sourceTopicName)
}

internal fun <K: Any, V : Any> materialized(
    store: Store<K, V>,
): Materialized<K, V?, KeyValueStore<Bytes, ByteArray>> {
    return Materialized.`as`<K, V, KeyValueStore<Bytes, ByteArray>>(store.name)
        .withKeySerde(store.serde.key)
        .withValueSerde(store.serde.value)
}

internal fun <K: Any, V : Any> materialized(table: Table<K, V>): Materialized<K, V?, KeyValueStore<Bytes, ByteArray>> {
    return Materialized.`as`<K, V, KeyValueStore<Bytes, ByteArray>>(table.stateStoreName)
        .withKeySerde(table.sourceTopic.serdes.key)
        .withValueSerde(table.sourceTopic.serdes.value)
}

@Suppress("UNCHECKED_CAST")
internal fun <K: Any, V> KStream<K, V>.filterNotNull(): KStream<K, V & Any> {
    val filteredInternalKStream = filter { _, value -> value != null }
    return filteredInternalKStream as KStream<K, V & Any>
}

@Suppress("UNCHECKED_CAST")
internal fun <K: Any, V> org.apache.kafka.streams.kstream.KTable<K, V>.skipTombstone(
    table: Table<K, V & Any>
): org.apache.kafka.streams.kstream.KTable<K, V & Any> {
    val named = Named.`as`("skip-table-${table.sourceTopicName}-tombstone")
    val filteredInternalKTable = filter({ _, value -> value != null }, named)
    return filteredInternalKTable as org.apache.kafka.streams.kstream.KTable<K, V & Any>
}

internal fun <K: Any, V> KStream<K, V>.skipTombstone(topic: Topic<K, V & Any>): KStream<K, V & Any> {
    return skipTombstone(topic, "")
}

@Suppress("UNCHECKED_CAST")
internal fun <K: Any, V> KStream<K, V>.skipTombstone(
    topic: Topic<K, V & Any>,
    namedSuffix: String,
): KStream<K, V & Any> {
    val named = Named.`as`("skip-${topic.name}-tombstone$namedSuffix")
    val filteredInternalStream = filter({ _, value -> value != null }, named)
    return filteredInternalStream as KStream<K, V & Any>
}

