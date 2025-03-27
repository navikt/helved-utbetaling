package libs.kafka

import org.apache.kafka.streams.kstream.*

open class Topic<K: Any, V : Any>(
    val name: String,
    val serdes: Serdes<K, V>,
) {
    internal fun consumed(named: String): Consumed<K, V> {
        return Consumed.with(serdes.key, serdes.value).withName(named)
    }

    internal open fun produced(named: String): Produced<K, V> {
        return Produced.with(serdes.key, serdes.value).withName(named)
    }

    internal infix fun <U : Any> join(right: KTable<K, U>): Joined<K, V, U> {
        return Joined.with(
            serdes.key,
            serdes.value,
            right.table.sourceTopic.serdes.value,
            "$name-join-${right.table.sourceTopic.name}",
        )
    }

    internal infix fun <U : Any> leftJoin(right: KTable<K, U>): Joined<K, V, U?> {
        return Joined.with(
            serdes.key,
            serdes.value,
            right.table.sourceTopic.serdes.value,
            "$name-left-join-${right.table.sourceTopic.name}",
        )
    }
}
