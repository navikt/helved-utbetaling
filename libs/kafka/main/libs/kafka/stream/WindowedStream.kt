package libs.kafka.stream

import kotlin.time.Duration.Companion.seconds
import libs.kafka.*
import libs.kafka.processor.LogProduceStateStoreProcessor
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.SessionWindowedKStream
import org.apache.kafka.streams.kstream.Suppressed
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.kstream.TimeWindowedKStream
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.state.SessionStore
import org.apache.kafka.streams.state.WindowStore

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

    fun reduce(
        supplier: ProcessorSupplier<Windowed<K>, V, K, V>,
        storeName: StateStoreName,
        acc: (V, V) -> V,
    ): ConsumedStream<K, V> {
        val named = "${namedSupplier()}-reduced"

        val materialized = Materialized.`as`<K, V, SessionStore<Bytes, ByteArray>>("suppress-${namedSupplier()}-aggregate")
            .withKeySerde(serdes.key)
            .withValueSerde(serdes.value)

        val reducedStream = stream
            .reduce(acc, Named.`as`("${namedSupplier()}-operation-reduced"), materialized)
            .toStream()
            .process(supplier, Named.`as`("suppress-${namedSupplier()}"), storeName)
            .selectKey { key, _ -> key }

        return ConsumedStream(reducedStream, { named }) 
    }
}
