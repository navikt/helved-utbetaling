package libs.kafka.stream

import libs.kafka.KTable
import libs.kafka.Named
import libs.kafka.Serdes
import libs.kafka.StateStoreName
import libs.kafka.Table
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.SessionWindows
import org.apache.kafka.streams.state.KeyValueStore
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class GroupedStream<K: Any, V: Any> internal constructor(
    private val stream: KGroupedStream<K, V>,
) {

    fun <U: Any> aggregate(
        table: Table<K, Set<U>>,
        aggregator: (K, V, Set<U>) -> Set<U>,
    ): KTable<K, Set<U>> {
        val ktable = stream.aggregate( 
            { emptySet() },
            aggregator,
            Named("${table.stateStoreName}-aggregate").into(),
            Materialized.`as`<K, Set<U>, KeyValueStore<Bytes, ByteArray>>(Named("${table.stateStoreName}-materialized").toString())
                .withKeySerde(table.serdes.key)
                .withValueSerde(table.serdes.value)
        )

        return KTable(table, ktable)
    }

    fun windowedBy(
        serdes: Serdes<K, V>,
        inactivityGap: Duration,
    ): SessionWindowedStream<K, V> {
        val sessionWindows = SessionWindows.ofInactivityGapAndGrace(inactivityGap.toJavaDuration(), inactivityGap.toJavaDuration())
        return SessionWindowedStream(serdes, stream.windowedBy(sessionWindows))
    }
}

