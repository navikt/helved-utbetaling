package libs.kafka.processor

import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record

class EnrichHeaderProcessor<K: Any, V>(
    private val headerKey: String,
    private val header: (V) -> String,
) : Processor<K, V, K, V> {
    private lateinit var context: ProcessorContext<K, V>

    override fun init(ctx: ProcessorContext<K, V>) {
        context = ctx
    }

    override fun process(record: Record<K, V>) {
        val headerValue = header(record.value()) 
        record.headers().add(RecordHeader(headerKey, headerValue.toByteArray()))
        context.forward(record)
    }
}

