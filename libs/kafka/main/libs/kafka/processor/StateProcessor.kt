package libs.kafka.processor

import libs.kafka.StateStoreName
import libs.kafka.KTable
import libs.kafka.KeyValue
import libs.kafka.processor.ProcessorMetadata
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.FixedKeyProcessor
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext
import org.apache.kafka.streams.processor.api.FixedKeyRecord
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import kotlin.jvm.optionals.getOrNull

internal interface KStateProcessor<K: Any, V, U, R> {
    fun process(
        metadata: ProcessorMetadata,
        store: TimestampedKeyValueStore<K, V>,
        keyValue: KeyValue<K, U>,
    ): R
}

abstract class StateProcessor<K: Any, V : Any, U, R>(
    private val named: String,
    private val storeName: StateStoreName,
) : KStateProcessor<K, V, U, R> {
    internal companion object {
        internal fun <K: Any, V : Any, U, R> KStream<K, U>.addProcessor(
            processor: StateProcessor<K, V, U, R>
        ): KStream<K, R> = processValues(
            { processor.run(StateProcessor<K, V, U, R>::InternalProcessor) },
            Named.`as`("stateful-operation-${processor.named}"),
            processor.storeName,
        )
    }

    private inner class InternalProcessor : FixedKeyProcessor<K, U, R> {
        private lateinit var context: FixedKeyProcessorContext<K, R>
        private lateinit var store: TimestampedKeyValueStore<K, V>

        override fun init(context: FixedKeyProcessorContext<K, R>) {
            this.context = context
            this.store = context.getStateStore(storeName)
        }

        override fun process(record: FixedKeyRecord<K, U>) {
            val recordMeta = requireNotNull(context.recordMetadata().getOrNull()) {
                "Denne er bare null når man bruker punctuators. Det er feil å bruke denne klassen til punctuation."
            }

            val metadata = ProcessorMetadata(
                topic = recordMeta.topic(),
                partition = recordMeta.partition(),
                offset = recordMeta.offset(),
                timestamp = record.timestamp(),
                systemTimeMs = context.currentSystemTimeMs(),
                streamTimeMs = context.currentStreamTimeMs(),
            )

            val valueToForward: R = process(
                metadata = metadata,
                store = store,
                keyValue = KeyValue(record.key(), record.value()),
            )

            context.forward(record.withValue(valueToForward))
        }
    }
}

