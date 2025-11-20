package libs.kafka.processor

import libs.kafka.KeyValue
import libs.kafka.Table
import libs.kafka.kafkaLog
import libs.kafka.StateStoreName
import libs.kafka.Topic
import libs.utils.secureLog
import net.logstash.logback.argument.StructuredArguments.kv
import org.apache.kafka.common.header.Headers
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class LogConsumeTopicProcessor<K: Any, V>(
    private val topic: Topic<K, V & Any>,
) : Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val metadata = context.recordMetadata()!!.get()
        kafkaLog.trace(
            "consume ${record.key()} on ${metadata.topic() ?: ""}",
            kv("key", record.key()),
            kv("topic", metadata.topic() ?: ""),
            kv("partition", metadata.partition()),
            kv("offset", metadata.offset()),
        )
        secureLog.trace(
            "consume ${record.key()} on ${metadata.topic() ?: ""} with ${record.value()}",
            kv("key", record.key()),
            kv("topic", metadata.topic() ?: ""),
            kv("partition", metadata.partition()),
            kv("offset", metadata.offset()),
        )
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
        kafkaLog.trace(
            "materialize ${record.key()} on $name",
            kv("key", record.key()),
            kv("store", name),
            kv("partition", metadata.partition()),
        )
        secureLog.trace(
            "materialize ${record.key()} on $name with ${record.value()}",
            kv("key", record.key()),
            kv("store", name),
            kv("partition", metadata.partition()),
        )
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
        kafkaLog.trace(
            "materialize ${record.key()} on ${table.sourceTopicName}",
            kv("key", record.key()),
            kv("table", table.sourceTopicName),
            kv("store", table.stateStoreName),
            kv("partition", metadata.partition()),
        )
        secureLog.trace(
            "materialize ${record.key()} on ${table.sourceTopicName} with ${record.value()}",
            kv("key", record.key()),
            kv("table", table.sourceTopicName),
            kv("store", table.stateStoreName),
            kv("partition", metadata.partition()),
        )
        context.forward(record)
    }
}

internal class LogProduceTopicProcessor<K: Any, V> internal constructor(
    private val topic: Topic<K, V & Any>,
) : Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val metadata = context.recordMetadata()!!.get()
        kafkaLog.trace(
            "produce ${record.key()} on ${topic.name}",
            kv("key", record.key()),
            kv("source_topic", metadata.topic() ?: ""),
            kv("topic", topic.name),
            kv("partition", metadata.partition()),
        )
        secureLog.trace(
            "produce ${record.key()} on ${topic.name} with ${record.value()}",
            kv("key", record.key()),
            kv("source_topic", metadata.topic() ?: ""),
            kv("topic", topic.name),
            kv("partition", metadata.partition()),
        )
        context.forward(record)
    }
}

