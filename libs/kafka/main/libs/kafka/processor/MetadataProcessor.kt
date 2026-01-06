package libs.kafka.processor

import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record

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
    val headers: Map<String, String> = emptyMap(),
)

class EnrichMetadataProcessor<K: Any, V>(
) : Processor<K, V, K, Pair<V, Metadata>> {
    private lateinit var context: ProcessorContext<K, Pair<V, Metadata>>

    override fun init(ctx: ProcessorContext<K, Pair<V, Metadata>>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val recordMeta = context.recordMetadata().orElse(null)

        val metadata = Metadata(
            topic = recordMeta?.topic() ?: "",
            partition = recordMeta?.partition() ?: -1,
            offset = recordMeta?.offset() ?: -1,
            timestamp = record.timestamp(),
            systemTimeMs = context.currentSystemTimeMs(),
            streamTimeMs = context.currentStreamTimeMs(),
            headers = record.headers().associate { it.key() to String(it.value(), Charsets.UTF_8) }
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
        val recordMeta = context.recordMetadata().orElse(null)
        val metadata = Metadata(
            topic = recordMeta?.topic() ?: "",
            partition = recordMeta?.partition() ?: -1,
            offset = recordMeta?.offset() ?: -1,
            timestamp = record.timestamp(),
            systemTimeMs = context.currentSystemTimeMs(),
            streamTimeMs = context.currentStreamTimeMs(),
        )
        peek(record.key(), record.value(), metadata)
        context.forward(record)
    }
}
