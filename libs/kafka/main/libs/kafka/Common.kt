package libs.kafka

import libs.utils.logger
import net.logstash.logback.argument.StructuredArgument
import org.apache.kafka.streams.KeyValue

val kafkaLog = logger("kafka")

data class StreamsPair<L, R>(
    val left: L,
    val right: R,
) 

data class KeyValue<K, V>(
    val key: K,
    val value: V,
) {
    internal fun toInternalKeyValue(): KeyValue<K, V> {
        return KeyValue(key, value)
    }
}

fun <K, V> Pair<K, V>.toInternalKeyValue(): KeyValue<K, V> {
    return KeyValue(first, second)
}

