package libs.kafka.processor

import libs.kafka.KTable
import libs.kafka.StateStore
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.FixedKeyProcessor
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext
import org.apache.kafka.streams.processor.api.FixedKeyRecord
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal interface ScheduleProcessor<K: Any, V> {
    fun schedule(wallClockTime: Long, store: StateStore<K, ValueAndTimestamp<V>>)
} 

abstract class StateScheduleProcessor<K: Any, V: Any>(
    private val named: String,
    private val table: KTable<K, V>,
    private val interval: Duration,

): ScheduleProcessor<K, V> {

    internal fun addToStreams() {
        table.internalKTable.toStream().processValues(
            { InternalProcessor(table.table.stateStoreName) },
            Named.`as`(named),
            table.table.stateStoreName,
        )
    }

    private inner class InternalProcessor(private val stateStoreName: String): FixedKeyProcessor<K, V?, V> {
        override fun process(record: FixedKeyRecord<K, V?>) {}

        override fun init(context: FixedKeyProcessorContext<K, V>) {
            val store: TimestampedKeyValueStore<K, V> = context.getStateStore(stateStoreName)
            context.schedule(interval.toJavaDuration(), PunctuationType.WALL_CLOCK_TIME) { wallClockTime ->
                schedule(wallClockTime, StateStore(store))
            }
        }
    }
}
