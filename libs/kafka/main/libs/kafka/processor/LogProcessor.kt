package libs.kafka.processor

import libs.kafka.*
import libs.utils.secureLog
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class LogConsumeTopicProcessor<K: Any, V>(
    private val topic: Topic<K, V & Any>,
    namedSuffix: String = "",
) : Processor<K, V, V>("log-consume-${topic.name}$namedSuffix") {
    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<K, V>): V {
        kafkaLog.trace(
            "consume ${keyValue.key} on ${metadata.topic}",
            kv("key", keyValue.key),
            kv("topic", metadata.topic),
            kv("partition", metadata.partition),
            kv("offset", metadata.offset),
        )
        secureLog.trace(
            "consume ${keyValue.key} on ${metadata.topic} with ${keyValue.value}",
            kv("key", keyValue.key),
            kv("topic", metadata.topic),
            kv("partition", metadata.partition),
            kv("offset", metadata.offset),
        )
        return keyValue.value
    }
}

internal class LogProduceStateStoreProcessor<K: Any, V>(
    private val name: StateStoreName,
): Processor<K, V, V>("log-produced-$name") {
    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<K, V>): V {
        kafkaLog.trace(
            "materialize ${keyValue.key} on $name",
            kv("key", keyValue.key),
            kv("store", name),
            kv("partition", metadata.partition),
        )
        secureLog.trace(
            "materialize ${keyValue.key} on $name with ${keyValue.value}",
            kv("key", keyValue.key),
            kv("store", name),
            kv("partition", metadata.partition),
        )
        return keyValue.value
    }
}

internal class LogProduceTableProcessor<K: Any, V>(
    private val table: Table<K, V & Any>,
) : Processor<K, V, V>("log-produced-${table.sourceTopicName}") {
    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<K, V>): V {
        kafkaLog.trace(
            "materialize ${keyValue.key} on ${table.sourceTopicName}",
            kv("key", keyValue.key),
            kv("table", table.sourceTopicName),
            kv("store", table.stateStoreName),
            kv("partition", metadata.partition),
        )
        secureLog.trace(
            "materialize ${keyValue.key} on ${table.sourceTopicName} with ${keyValue.value}",
            kv("key", keyValue.key),
            kv("table", table.sourceTopicName),
            kv("store", table.stateStoreName),
            kv("partition", metadata.partition),
        )
        return keyValue.value
    }
}

internal class LogProduceTopicProcessor<K: Any, V> internal constructor(
    named: String,
    private val topic: Topic<K, V & Any>,
) : Processor<K, V, V>(named) {
    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<K, V>): V {
        kafkaLog.trace(
            "produce ${keyValue.key} on ${topic.name}",
            kv("key", keyValue.key),
            kv("source_topic", metadata.topic),
            kv("topic", topic.name),
            kv("partition", metadata.partition),
        )
        secureLog.trace(
            "produce ${keyValue.key} on ${topic.name} with ${keyValue.value}",
            kv("key", keyValue.key),
            kv("source_topic", metadata.topic),
            kv("topic", topic.name),
            kv("partition", metadata.partition),
        )
        return keyValue.value
    }
}

