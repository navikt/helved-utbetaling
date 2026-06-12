package libs.kafka.processor

import libs.kafka.StateStoreName
import libs.kafka.Table
import libs.kafka.Topic
import libs.kafka.kafkaLog
import libs.utils.secureLog
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record

internal class LogConsumeTopicProcessor<K: Any, V>(
    private val topic: Topic<K, V & Any>,
) : Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val metadata = context.recordMetadata()!!.get()

        kafkaLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("topic", metadata.topic() ?: "")
            .addKeyValue("partition", metadata.partition())
            .addKeyValue("offset", metadata.offset())
            .log("consume ${record.key()} on ${metadata.topic() ?: ""}")

        secureLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("topic", metadata.topic() ?: "")
            .addKeyValue("partition", metadata.partition())
            .addKeyValue("offset", metadata.offset())
            .log("consume ${record.key()} on ${metadata.topic() ?: ""} with ${record.value()}")

        context.forward(record)
    }
}

internal class LogProduceStateStoreProcessor<K: Any, V>(
    named: String,
    private val name: StateStoreName,
): Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val metadata = context.recordMetadata()!!.get()

        kafkaLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("store", name)
            .addKeyValue("partition", metadata.partition())
            .log("materialize ${record.key()} on $name")

        secureLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("store", name)
            .addKeyValue("partition", metadata.partition())
            .log("materialize ${record.key()} on $name with ${record.value()}")

        context.forward(record)
    }
}

internal class LogProduceTableProcessor<K: Any, V>(
    private val table: Table<K, V & Any>,
) : Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val metadata = context.recordMetadata()!!.get()

        kafkaLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("table", table.sourceTopicName)
            .addKeyValue("store", table.stateStoreName)
            .addKeyValue("partition", metadata.partition())
            .log("materialize ${record.key()} on ${table.sourceTopicName}")
        
        secureLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("table", table.sourceTopicName)
            .addKeyValue("store", table.stateStoreName)
            .addKeyValue("partition", metadata.partition())
            .log("materialize ${record.key()} on ${table.sourceTopicName} with ${record.value()}")

        context.forward(record)
    }
}

@Deprecated("does not audit", replaceWith = ReplaceWith("LogAndAuditProduceTopicProcessor"))
internal class LogProduceTopicProcessor<K: Any, V> internal constructor(
    private val topic: Topic<K, V & Any>,
) : Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val metadata = context.recordMetadata()!!.get()

        kafkaLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("source_topic", metadata.topic() ?: "")
            .addKeyValue("topic", topic.name)
            .addKeyValue("partition", metadata.partition())
            .log("produce ${record.key()} on ${topic.name}")

        secureLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("source_topic", metadata.topic() ?: "")
            .addKeyValue("topic", topic.name)
            .addKeyValue("partition", metadata.partition())
            .log("produce ${record.key()} on ${topic.name} with ${record.value()}")

        context.forward(record)
    }
}

const val AUD_TIMESTAMP_MS = "x-ts"
const val AUD_STREAM_TIME_MS = "x-st"
const val AUD_SYSTEM_TIME_MS = "x-sy"

internal class LogAndAuditProduceTopicProcessor<K: Any, V> internal constructor(
    private val topic: Topic<K, V & Any>,
) : Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val metadata = context.recordMetadata().orElse(null)

        kafkaLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("source_topic", metadata?.topic() ?: "")
            .addKeyValue("topic", topic.name)
            .addKeyValue("partition", metadata?.partition() ?: "")
            .log("produce ${record.key()} on ${topic.name}")

        secureLog.atTrace()
            .addKeyValue("key", record.key())
            .addKeyValue("source_topic", metadata?.topic() ?: "")
            .addKeyValue("topic", topic.name)
            .addKeyValue("partition", metadata?.partition() ?: "")
            .log("produce ${record.key()} on ${topic.name} with ${record.value()}")

        fun replaceHeader(key: String, time: Long) {
            record.headers().remove(key)
            record.headers().add(RecordHeader(key, time.toString().toByteArray(Charsets.UTF_8)))
        }

        replaceHeader(AUD_TIMESTAMP_MS, record.timestamp())
        replaceHeader(AUD_STREAM_TIME_MS, context.currentStreamTimeMs())
        replaceHeader(AUD_SYSTEM_TIME_MS, context.currentSystemTimeMs())
        context.forward(record)
    }
}
