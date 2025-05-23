package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.LogProduceStateStoreProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.common.utils.Bytes

class GroupedStream<K: Any, V: Any> internal constructor(
    private val stream: KGroupedStream<K, V>,
    private val namedSupplier: () -> String,
) {

    fun <U: Any> aggregate(
        table: Table<K, Set<U>>,
        aggregator: (K, V, Set<U>) -> Set<U>,
    ): KTable<K, Set<U>> {
        val ktable = stream.aggregate( 
            {emptySet()},
            aggregator,
            Materialized.`as`<K, Set<U>, KeyValueStore<Bytes, ByteArray>>("${namedSupplier()}-aggregate-store")
                .withKeySerde(table.serdes.key)
                .withValueSerde(table.serdes.value)
        )

        return KTable(table, ktable)
    }
}
