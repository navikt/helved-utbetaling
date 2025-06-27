package libs.kafka

import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore

typealias StateStoreName = String

open class Store<K : Any, V : Any> private constructor(
    private val sourceTable: Table<K, V>?,
    val name: StateStoreName,
    val serde: Serdes<K, V>
) {
    constructor(sourceTable: Table<K, V>): this(sourceTable, sourceTable.stateStoreName, sourceTable.serdes)
    constructor(name: StateStoreName, serde: Serdes<K, V>): this(null, name, serde)
}

class KStore<K : Any, V : Any>(
    val store: Store<K, V>,
    val internalKTable: org.apache.kafka.streams.kstream.KTable<K, V>,
) {
    fun <U : Any> join(table: KTable<K, U>): ConsumedStream<K, StreamsPair<V?, U?>> =
        ConsumedStream(internalKTable.join(table.internalKTable, ::StreamsPair).toStream())
}

class StateStore<K : Any, V>(private val internalStateStore: ReadOnlyKeyValueStore<K, V>) {
    fun getOrNull(key: K): V? = internalStateStore.get(key)

    fun iterator(): Iterator<KeyValue<K, V>> = internalStateStore.all().iterator()
    fun filter(limit: Int = 1000, filter: (KeyValue<K, V>) -> Boolean): List<Pair<K, V>> {
        val seq = sequence<Pair<K, V>> {
            val iter = internalStateStore.all()
            for(i in 0 until limit) {
                if (!iter.hasNext()) break
                val next = iter.next()
                if (filter(next)) yield(next.key to next.value)
            }
            iter.close()
        }
        return seq.toList()
    }
}

