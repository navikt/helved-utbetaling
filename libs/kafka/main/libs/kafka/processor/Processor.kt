package libs.kafka.processor

import libs.kafka.*
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.processor.api.*
import kotlin.jvm.optionals.getOrNull

internal interface KProcessor<K, V, U> {
    fun process(metadata: ProcessorMetadata, keyValue: KeyValue<K, V>): U
}

abstract class Processor<K: Any, V, U>(private val named: String? = null) : KProcessor<K, V, U> {
    internal companion object {
        internal fun <K: Any, V, U> KStream<K, V>.addProcessor(processor: Processor<K, V, U>): KStream<K, U> =
            when (processor.named) {
                null -> processValues({ processor.run { InternalProcessor() } })
                else -> processValues({ processor.run { InternalProcessor() } }, Named(processor.named).into())
            }
    }

    private inner class InternalProcessor : FixedKeyProcessor<K, V, U> {
        private lateinit var context: FixedKeyProcessorContext<K, U>

        override fun init(context: FixedKeyProcessorContext<K, U>) {
            this.context = context
        }

        override fun process(record: FixedKeyRecord<K, V>) {
            val recordMeta = requireNotNull(context.recordMetadata().getOrNull()) {
                "Denne er bare null når man bruker punctuators. Det er feil å bruke denne klassen til punctuation."
            }

            val metadata = ProcessorMetadata(
                topic = recordMeta.topic() ?: "no topic",
                partition = recordMeta.partition(),
                offset = recordMeta.offset(),
                timestamp = record.timestamp(),
                systemTimeMs = context.currentSystemTimeMs(),
                streamTimeMs = context.currentStreamTimeMs(),
            )

            val valueToForward: U = process(
                metadata = metadata,
                keyValue = KeyValue(record.key(), record.value()),
            )

            context.forward(record.withValue(valueToForward))
        }
    }
}

/**
 * @param timestamp: The current timestamp in the producers environment
 * @param systemTimeMs: Current system timestamp (wall-clock-time)
 * @param streamTimeMs: The largest timestamp seen so far, and it only moves forward
 */
data class ProcessorMetadata(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long,
    val systemTimeMs: Long,
    val streamTimeMs: Long,
)

//internal class MetadataProcessor<T : Any>(
//    topic: Topic<T>,
//) : Processor<T?, Pair<KeyValue<String, T?>, ProcessorMetadata>>(
//    "from-${topic.name}-enrich-metadata",
//) {
//    override fun process(
//        metadata: ProcessorMetadata,
//        keyValue: KeyValue<String, T?>,
//    ): Pair<KeyValue<String, T?>, ProcessorMetadata> =
//        keyValue to metadata
//}

internal class MetadataProcessor<K: Any, V>(): Processor<K, V, Pair<KeyValue<K, V>, ProcessorMetadata>>() {
    override fun process(
        metadata: ProcessorMetadata,
        keyValue: KeyValue<K, V>,
    ): Pair<KeyValue<K, V>, ProcessorMetadata> {
        return keyValue to metadata 
    } 
}

