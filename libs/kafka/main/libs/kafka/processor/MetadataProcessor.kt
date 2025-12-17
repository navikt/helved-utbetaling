package libs.kafka.processor

import libs.kafka.KeyValue
import libs.kafka.Named
import libs.kafka.Topic
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.Headers
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import kotlin.jvm.optionals.getOrNull

/**
 * @param timestamp: The current timestamp in the producers environment
 * @param systemTimeMs: Current system timestamp (wall-clock-time)
 * @param streamTimeMs: The largest timestamp seen so far, and it only moves forward
 */
data class Metadata(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long,
    val systemTimeMs: Long,
    val streamTimeMs: Long,
)

class EnrichMetadataProcessor<K: Any, V>(
) : Processor<K, V, K, Pair<V, Metadata>> {
    private lateinit var context: ProcessorContext<K, Pair<V, Metadata>>

    override fun init(ctx: ProcessorContext<K, Pair<V, Metadata>>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val recordMeta = context.recordMetadata()!!.get()
        val metadata = Metadata(
            topic = recordMeta.topic() ?: "",
            partition = recordMeta.partition(),
            offset = recordMeta.offset(),
            timestamp = record.timestamp(),
            systemTimeMs = context.currentSystemTimeMs(),
            streamTimeMs = context.currentStreamTimeMs(),
        )
        val newValue = record.value() to metadata
        context.forward(record.withValue(newValue))
    }
}

class PeekMetadataProcessor<K: Any, V>(
    private val peek: (K, V, Metadata) -> Unit,
): Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val recordMeta = context.recordMetadata()!!.get()
        val metadata = Metadata(
            topic = recordMeta.topic() ?: "",
            partition = recordMeta.partition(),
            offset = recordMeta.offset(),
            timestamp = record.timestamp(),
            systemTimeMs = context.currentSystemTimeMs(),
            streamTimeMs = context.currentStreamTimeMs(),
        )
        peek(record.key(), record.value(), metadata)
        context.forward(record)
    }
}
