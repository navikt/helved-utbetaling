package libs.kafka

import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp

typealias StateStoreName = String

open class Store<K : Any, V : Any> private constructor(
    private val sourceTable: Table<K, V>?,
    val name: StateStoreName,
    val serde: Serdes<K, V>
) {
    constructor(sourceTable: Table<K, V>) : this(sourceTable, sourceTable.stateStoreName, sourceTable.serdes)
    constructor(name: StateStoreName, serde: Serdes<K, V>) : this(null, name, serde)
}

class KStore<K : Any, V : Any>(
    val store: Store<K, V>,
    val internalKTable: org.apache.kafka.streams.kstream.KTable<K, V>,
) {
    fun <U : Any> join(table: KTable<K, U>): ConsumedStream<K, StreamsPair<V, U?>> =
        ConsumedStream(internalKTable.join(table.internalKTable, ::StreamsPair).toStream()) {
            "consume-${store.name}-join-${table.table.stateStoreName}"
        }
}

class StateStore<K : Any, V>(private val internalStateStore: TimestampedKeyValueStore<K, V>) {
    fun getOrNull(key: K): ValueAndTimestamp<V>? = internalStateStore.get(key)

    fun iterator(): Iterator<KeyValue<K, ValueAndTimestamp<V>>> = internalStateStore.all().iterator()
    fun filter(limit: Int = 1000, filter: (KeyValue<K, V>) -> Boolean): List<Pair<K, V>> {
        val seq = sequence<Pair<K, V>> {
            val iter = internalStateStore.all()
            for (i in 0 until limit) {
                if (!iter.hasNext()) break
                val next = iter.next()
                if (filter(
                        KeyValue(next.key, next.value.value())
                    )
                ) yield(next.key to next.value.value())
            }
            iter.close()
        }
        return seq.toList()
    }
}

