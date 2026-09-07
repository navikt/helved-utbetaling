package simulering

import libs.kafka.Store
import libs.kafka.processor.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.StoreBuilder
import org.apache.kafka.streams.state.Stores
import org.apache.kafka.streams.state.TimestampedKeyValueStoreWithHeaders
import org.apache.kafka.streams.state.ValueTimestampHeaders
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest

internal fun simuleringHeadersProcessor(
    store: Store<String, SimulerBeregningRequest>,
): Processor<String, SimulerBeregningRequest?, String, SimulerBeregningRequest?> = Processor(object : ProcessorSupplier<String, SimulerBeregningRequest?, String, SimulerBeregningRequest?> {
    override fun stores(): Set<StoreBuilder<*>> {
        val inner = Stores.persistentTimestampedKeyValueStoreWithHeaders(store.name)
        return setOf(Stores.timestampedKeyValueStoreWithHeadersBuilder(inner, store.serde.key, store.serde.value))
    }

    override fun get(): org.apache.kafka.streams.processor.api.Processor<String, SimulerBeregningRequest?, String, SimulerBeregningRequest?> =
        object : org.apache.kafka.streams.processor.api.Processor<String, SimulerBeregningRequest?, String, SimulerBeregningRequest?> {
            private lateinit var headers: TimestampedKeyValueStoreWithHeaders<String, SimulerBeregningRequest>
            private lateinit var context: ProcessorContext<String, SimulerBeregningRequest?>

            override fun init(context: ProcessorContext<String, SimulerBeregningRequest?>) {
                this.context = context
                headers = context.getStateStore(store.name) as TimestampedKeyValueStoreWithHeaders<String, SimulerBeregningRequest>
            }

            override fun process(record: Record<String, SimulerBeregningRequest?>) {
                record.value()?.let { headers.put(record.key(), ValueTimestampHeaders.make(it, record.timestamp(), record.headers())) }
                    ?: headers.delete(record.key())
                context.forward(record)
            }
        }
})
