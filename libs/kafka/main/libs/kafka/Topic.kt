package libs.kafka

import org.apache.kafka.streams.kstream.*

open class Topic<K: Any, V : Any>(
    val name: String,
    val serdes: Serdes<K, V>,
) {
    internal fun consumed(named: String = "consume-$name"): Consumed<K, V> {
        return Consumed.with(serdes.key, serdes.value).withName(Named(named).toString())
    }

    internal open fun produced(): Produced<K, V> {
        return Produced.with(serdes.key, serdes.value)
    }

    internal infix fun <U : Any> join(right: KTable<K, U>): Joined<K, V, U> {
        return Joined.with(
            serdes.key,
            serdes.value,
            right.table.sourceTopic.serdes.value,
            Named("$name-join-${right.table.sourceTopicName}").toString(),
        )
    }

    internal infix fun <U : Any> leftJoin(right: KTable<K, U>): Joined<K, V, U?> {
        return Joined.with(
            serdes.key,
            serdes.value,
            right.table.sourceTopic.serdes.value,
            Named("$name-leftjoin-${right.table.sourceTopicName}").toString(),
        )
    }
}
