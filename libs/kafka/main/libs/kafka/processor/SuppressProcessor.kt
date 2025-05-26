package libs.kafka.processor

import libs.kafka.StateStoreName
import libs.kafka.StreamSerde
import libs.kafka.StreamsPair
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.state.StoreBuilder
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class SuppressProcessor<K: Any, V: Any>(
    private val punctuationInterval: Duration,
    private val inactivityGap: Duration,
    private val stateStoreName: StateStoreName,
): Processor<Windowed<K>, List<StreamsPair<V, V?>>, K, List<StreamsPair<V, V?>>> {

    companion object {
        fun <K: Any, V: Any> supplier(
            keySerde: StreamSerde<Windowed<K>>,
            valueSerde: StreamSerde<List<StreamsPair<V, V?>>>,
            punctuationInterval: Duration,
            inactivityGap: Duration,
            stateStoreName: StateStoreName,
        ): ProcessorSupplier<Windowed<K>, List<StreamsPair<V, V?>>, K, List<StreamsPair<V, V?>>> {
            return object: ProcessorSupplier<Windowed<K>, List<StreamsPair<V, V?>>, K, List<StreamsPair<V, V?>>> {

                override fun stores(): Set<StoreBuilder<*>> {
                    val store = org.apache.kafka.streams.state.Stores.persistentTimestampedKeyValueStore(stateStoreName)
                    return setOf(org.apache.kafka.streams.state.Stores.timestampedKeyValueStoreBuilder(store, keySerde, valueSerde))
                }

                override fun get(): Processor<Windowed<K>, List<StreamsPair<V, V?>>, K, List<StreamsPair<V, V?>>> {
                    return SuppressProcessor(punctuationInterval, inactivityGap, stateStoreName)
                }
            }

        } 
    }

    private lateinit var bufferStore: TimestampedKeyValueStore<Windowed<K>, List<StreamsPair<V, V?>>>
    private lateinit var context: ProcessorContext<K, List<StreamsPair<V, V?>>>

    override fun init(ctx: ProcessorContext<K, List<StreamsPair<V, V?>>>) {
        context = ctx
        bufferStore = context.getStateStore(stateStoreName) as TimestampedKeyValueStore<Windowed<K>, List<StreamsPair<V, V?>>>
        context.schedule(punctuationInterval.toJavaDuration(), PunctuationType.WALL_CLOCK_TIME, ::punctuate)
    } 

    override fun process(record: org.apache.kafka.streams.processor.api.Record<Windowed<K>, List<StreamsPair<V, V?>>>) {
        bufferStore.put(record.key(), ValueAndTimestamp.make(record.value(), record.timestamp()))
    }

    private fun punctuate(wallClockTime: Long) {
        val iterator = bufferStore.all()
        val windowsToEmit = mutableListOf<Windowed<K>>()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val windowedKey = entry.key
            val valueAndTimestamp = entry.value
            val windowEndTime = windowedKey.window().endTime().toEpochMilli()
            val emissionTimeThreshold = windowEndTime + inactivityGap.inWholeMilliseconds

            if (wallClockTime > emissionTimeThreshold) {
                val latestAggregate = valueAndTimestamp.value() 
                val lastestRecordTimestamp = valueAndTimestamp.timestamp()
                context.forward(org.apache.kafka.streams.processor.api.Record(windowedKey.key(), latestAggregate, lastestRecordTimestamp))
                windowsToEmit.add(windowedKey)
            }
        }
        iterator.close()
        windowsToEmit.forEach(bufferStore::delete)
    }
}
