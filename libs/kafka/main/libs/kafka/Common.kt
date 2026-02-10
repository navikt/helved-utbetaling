package libs.kafka

import libs.utils.logger

val kafkaLog = logger("kafka")

data class StreamsPair<L, R>(
    val left: L,
    val right: R,
) 

data class KeyValue<K, V>(
    val key: K,
    val value: V,
) {
    internal fun toInternalKeyValue(): org.apache.kafka.streams.KeyValue<K, V> {
        return org.apache.kafka.streams.KeyValue(key, value)
    }
}

fun <K, V> Pair<K, V>.toInternalKeyValue(): org.apache.kafka.streams.KeyValue<K, V> {
    return org.apache.kafka.streams.KeyValue(first, second)
}

