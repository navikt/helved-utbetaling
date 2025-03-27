package libs.kafka.stream

import libs.kafka.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.*

class TimeWindowedStream<K: Any, V : Any> internal constructor(
    private val serdes: Serdes<K, V>,
    private val stream: TimeWindowedKStream<K, V>,
    private val namedSupplier: () -> String,
) {
    fun reduce(acc: (V, V) -> V): ConsumedStream<K, V> {
        val named = "${namedSupplier()}-reduced"

        val materialized = Materialized.`as`<K, V, WindowStore<Bytes, ByteArray>>("$named-store")
            .withKeySerde(serdes.key)
            .withValueSerde(serdes.value)

        val reducedStream = stream
            .reduce(acc, Named.`as`("${namedSupplier()}-operation-reduced"), materialized)
            .toStream()
            .selectKey { key, _ -> key.key() }

        return ConsumedStream(reducedStream, {named} ) 
    }
}

class SessionWindowedStream<K: Any, V : Any> internal constructor(
    private val serdes: Serdes<K, V>,
    private val stream: SessionWindowedKStream<K, V>,
    private val namedSupplier: () -> String,
) {
    fun reduce(acc: (V, V) -> V): ConsumedStream<K, V> {
        val named = "${namedSupplier()}-reduced"

        val materialized = Materialized.`as`<K, V, SessionStore<Bytes, ByteArray>>("$named-store")
            .withKeySerde(serdes.key)
            .withValueSerde(serdes.value)

        val reducedStream = stream
            .reduce(acc, Named.`as`("${namedSupplier()}-operation-reduced"), materialized)
            .toStream()
            .selectKey { key, _ -> key.key() }

        return ConsumedStream(reducedStream, { named }) 
    }
}
