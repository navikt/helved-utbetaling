package libs.kafka

import libs.kafka.stream.ConsumedStream
import libs.kafka.processor.StateScheduleProcessor
import org.apache.kafka.streams.kstream.KTable

data class Table<K: Any, V: Any>(
    val sourceTopic: Topic<K, V>,
    val serdes: Serdes<K, V> = sourceTopic.serdes,
    val stateStoreName: StateStoreName = "${sourceTopic.name}-state-store"
) {
    val sourceTopicName: String
        get() = sourceTopic.name
}

class KTable<K: Any, V : Any>(
    val table: Table<K, V>,
    val internalKTable: KTable<K, V?>,
) {
    internal val tombstonedInternalKTable: KTable<K, V> by lazy {
        internalKTable.skipTombstone(table)
    }

    fun toStream(): ConsumedStream<K, V> {
        val stream = internalKTable.toStream().skipTombstone(table.sourceTopic)
        return ConsumedStream(stream)
    }

    fun schedule(scheduler: StateScheduleProcessor<K, V>) {
        scheduler.addToStreams()
    }
}

