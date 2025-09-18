package libs.kafka.processor

import libs.kafka.Serdes
import libs.kafka.StateStoreName
import libs.kafka.kafkaLog
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.state.StoreBuilder
import org.apache.kafka.streams.state.Stores
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
* Skip duplicates occuring within 'retention' time.
* 
* Sometimes we have to produce messages downstream to external services.
* If the downstream service is not idempotent, we MUST avoid sending the same record twice. 
* This processor node will keep a list of 'seen' records by its key and value.hashcode 
* After configured retention is hit, the 'seen' record can now be processed again.
*
* NB! If downstream fails and the topology tries again, the seen node is now skipped.
* 
*/
class DedupProcessor<K: Any, V: Any> (
    private val stateStoreName: StateStoreName,
    private val retention: Duration,
    private val downstream: () -> Unit,
): Processor<K, V, K, V> {
    private lateinit var store: TimestampedKeyValueStore<String, V>
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
        store = context.getStateStore(stateStoreName) as TimestampedKeyValueStore<String, V>
        context.schedule(retention.toJavaDuration(), PunctuationType.WALL_CLOCK_TIME) { now ->
            val iter = store.all()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (now - entry.value.timestamp() > retention.inWholeMilliseconds) {
                    kafkaLog.debug("dedup reset key=${entry.key}")
                    store.delete(entry.key) 
                }
            }
            iter.close()
        }
    } 

    override fun process(record: Record<K, V>) {
        val dedupKey = "${record.key()}|${record.value().hashCode()}" // TODO: ensure hash works for e.g. jaxb
        val seen = store.get(dedupKey)
        val now = record.timestamp()
        if (seen == null || now - seen.timestamp() > retention.inWholeMilliseconds) {
            try {
                downstream()
                store.put(dedupKey, ValueAndTimestamp.make(record.value(), now))
                kafkaLog.debug("dedup allow key=${record.key()}")
                context.forward(record)
            } catch (e: Exception) {
                kafkaLog.warn("downstream failed, dedup will retry key=${record.key()}")
                throw e
            }
        } else {
            kafkaLog.debug("dedup deny key=${record.key()}")
        }
    }

    companion object {
        fun <K: Any, V: Any> supplier(
            serdes: Serdes<K, V>,
            retention: Duration,
            stateStoreName: StateStoreName,
            downstream: () -> Unit,
        ): ProcessorSupplier<K, V, K, V> {
            return object: ProcessorSupplier<K, V, K, V> {
                override fun stores(): Set<StoreBuilder<*>> = setOf(
                    Stores.timestampedKeyValueStoreBuilder(
                        Stores.persistentTimestampedKeyValueStore(stateStoreName),
                        serdes.key,
                        serdes.value
                    )
                )
                override fun get(): Processor<K, V, K, V> = DedupProcessor(stateStoreName, retention, downstream)
            }
        }
    }
}

