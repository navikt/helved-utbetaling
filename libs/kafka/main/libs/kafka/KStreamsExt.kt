@file:Suppress("UNCHECKED_CAST")

package libs.kafka

import libs.kafka.processor.LogProduceTableProcessor
import libs.kafka.processor.LogProduceTopicProcessor
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.Joined
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Repartitioned
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.kstream.KTable as _KTable

internal fun <K: Any, V : Any> KStream<K, V>.produceWithLogging(topic: Topic<K, V>) {
    val logger = LogProduceTopicProcessor(topic)
    return addProcessor(logger).to(topic.name, topic.produced())
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
    val joined = Joined.with(leftSerdes.key, leftSerdes.value, ktable.table.serdes.value, Named(named).toString())
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

internal fun <K: Any, V : Any> KStream<K, V?>.toKTable(
    table: Table<K, V>,
    named: String = "ktable-${table.sourceTopicName}"
): KTable<K, V> {
    val internalKTable = addProcessor(LogProduceTableProcessor(table)).toTable(Named(named).into(), materialized(table))
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

internal fun <K: Any, V> KStream<K, V>.filterNotNull(): KStream<K, V & Any> {
    return filter ({ _, value -> value != null }) as KStream<K, V & Any>
}

internal fun <K: Any, V> _KTable<K, V>.skipTombstone(
    table: Table<K, V & Any>,
    named: String = "ktable-${table.sourceTopicName}-skiptomb",
): _KTable<K, V & Any> {
    return filter({ _, value -> value != null } , Named(named).into()) as _KTable<K, V & Any>
}

internal fun <K: Any, V> KStream<K, V>.skipTombstone(topic: Topic<K, V & Any>): KStream<K, V & Any> {
    return filter({ _, value -> value != null }) as KStream<K, V & Any>
}

