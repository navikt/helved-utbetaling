package libs.kafka.stream

import libs.kafka.KTable
import libs.kafka.Named
import libs.kafka.Serdes
import libs.kafka.StateStoreName
import libs.kafka.Store
import libs.kafka.Table
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Aggregator
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.SessionWindowedKStream
import org.apache.kafka.streams.kstream.SessionWindows
import org.apache.kafka.streams.kstream.Suppressed
import org.apache.kafka.streams.kstream.TimeWindowedKStream
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.state.SessionStore
import org.apache.kafka.streams.state.Stores
import org.apache.kafka.streams.state.WindowStore
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class TimeWindowedStream<K: Any, V : Any> internal constructor(
    private val serdes: Serdes<K, V>,
    private val stream: TimeWindowedKStream<K, V>,
) {
    fun reduce(name: StateStoreName, acc: (V, V) -> V): ConsumedStream<K, V> {
        val materialized = Materialized.`as`<K, V, WindowStore<Bytes, ByteArray>>(name)
            .withKeySerde(serdes.key)
            .withValueSerde(serdes.value)

        val reducedStream = stream
            .reduce(acc, Named("$name-reduce").into(), materialized)
            .toStream()
            .selectKey ({ key, _ -> key.key() }, Named("rekey-$name").into())

        return ConsumedStream(reducedStream ) 
    }
}

class SessionWindowedStream<K: Any, V : Any> internal constructor(
    private val serdes: Serdes<K, V>,
    private val stream: SessionWindowedKStream<K, V>,
) {

    fun reduce(
        suppressSupplier: ProcessorSupplier<Windowed<K>, V, K, V>,
        dedupSupplier: ProcessorSupplier<K, V, K, V>,
        storeName: StateStoreName,
        acc: (V, V) -> V,
    ): ConsumedStream<K, V> {
        val materialized = Materialized.`as`<K, V, SessionStore<Bytes, ByteArray>>(Named("$storeName-materialized").toString())
            .withKeySerde(serdes.key)
            .withValueSerde(serdes.value)

        val reducedStream = stream
            .reduce(acc, Named("$storeName-reduce").into(), materialized)
            .toStream()
            .process(suppressSupplier, Named("$storeName-suppress").into(), storeName)
            .process(dedupSupplier, Named("$storeName-dedup").into())
            .selectKey { key, _ -> key }

        return ConsumedStream(reducedStream) 
    }

    fun aggregate(
        store: Store<K, List<V>>,
        named: String,
        aggregator: (K, V, List<V>) -> List<V>, 
    ): MappedStream<K, List<V>> {
        fun initializer(): List<V> = emptyList()
        fun sessionMerger(key: K, agg1: List<V>, agg2: List<V>): List<V> = (agg1 + agg2).toSet().toList()

        val materialized = Materialized.`as`<K, List<V>, SessionStore<Bytes, ByteArray>>(store.name)
            .withKeySerde(store.serde.key)
            .withValueSerde(store.serde.value)
        val ktable = stream.aggregate(
            ::initializer,
            aggregator,
            ::sessionMerger,
            Named(named).into(),
            materialized,
        )
        val suppressedKtable = ktable.suppress(Suppressed.untilTimeLimit(50.milliseconds.toJavaDuration(), Suppressed.BufferConfig.unbounded()))
        val suppressedStream = suppressedKtable.toStream().map { k, v -> KeyValue(k.key(), v)}
        return MappedStream(suppressedStream)
    }
}

