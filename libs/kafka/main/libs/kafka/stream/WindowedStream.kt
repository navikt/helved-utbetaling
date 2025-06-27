package libs.kafka.stream

import libs.kafka.Named
import libs.kafka.Serdes
import libs.kafka.StateStoreName
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.SessionWindowedKStream
import org.apache.kafka.streams.kstream.TimeWindowedKStream
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.state.SessionStore
import org.apache.kafka.streams.state.WindowStore

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
        supplier: ProcessorSupplier<Windowed<K>, V, K, V>,
        storeName: StateStoreName,
        acc: (V, V) -> V,
    ): ConsumedStream<K, V> {
        val materialized = Materialized.`as`<K, V, SessionStore<Bytes, ByteArray>>(Named("$storeName-materialized").toString())
            .withKeySerde(serdes.key)
            .withValueSerde(serdes.value)

        val reducedStream = stream
            .reduce(acc, Named("$storeName-reduce").into(), materialized)
            .toStream()
            .process(supplier, Named("$storeName-suppress").into(), storeName)
            .selectKey { key, _ -> key }

        return ConsumedStream(reducedStream) 
    }
}
