package libs.kafka

import io.opentelemetry.api.trace.Span
import libs.tracing.Tracing
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.processor.StateStore
import org.apache.kafka.streams.processor.StateStoreContext
import org.apache.kafka.streams.query.Position
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier
import org.apache.kafka.streams.state.KeyValueIterator
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.Stores
import java.nio.charset.StandardCharsets
import java.util.*

private const val TRACE_CONTEXT_W3C_LEN = 55
private const val VERSION_FLAG_LEN = 1
private const val V2_FLAG: Byte = 1

class TracingKeyValueStore(
    private val inner: KeyValueStore<Bytes, ByteArray>,
) : KeyValueStore<Bytes, ByteArray> {

    companion object {
        fun supplier(name: StateStoreName): KeyValueBytesStoreSupplier {
            return object : KeyValueBytesStoreSupplier {
                private val inner = Stores.persistentKeyValueStore(name)
                override fun name() = inner.name()
                override fun get(): KeyValueStore<Bytes, ByteArray> = TracingKeyValueStore(inner.get())
                override fun metricsScope(): String = "rocksdb"
            }
        }
    }

    private fun wrap(value: ByteArray?): ByteArray? {
        if (value == null) return null
        val traceparent = Tracing.getTraceparent() ?: return value
        val traceBytes = traceparent.toByteArray(StandardCharsets.UTF_8)
        val combined = Arrays.copyOf(value, value.size + VERSION_FLAG_LEN + TRACE_CONTEXT_W3C_LEN)
        combined[value.size] = V2_FLAG
        System.arraycopy(traceBytes, 0, combined, value.size + VERSION_FLAG_LEN, TRACE_CONTEXT_W3C_LEN)
        return combined
    }

    private fun unwrap(valueAndTrace: ByteArray?): Pair<ByteArray?, String?> {
        if (valueAndTrace == null) return null to null
        val totalTraceLen = VERSION_FLAG_LEN + TRACE_CONTEXT_W3C_LEN
        if (valueAndTrace.size < totalTraceLen) return valueAndTrace to null

        val flagIndex = valueAndTrace.size - totalTraceLen
        if (valueAndTrace[flagIndex] == V2_FLAG) {
            val payload = valueAndTrace.copyOfRange(0, flagIndex)
            val trace = String(valueAndTrace.copyOfRange(flagIndex + VERSION_FLAG_LEN, valueAndTrace.size), StandardCharsets.UTF_8)
            return payload to trace
        }
        return valueAndTrace to null
    }

    override fun get(key: Bytes): ByteArray? {
        val returned = inner.get(key) ?: return null
        val (value, trace) = unwrap(returned)
        
        Tracing.startSpan(
            name = "${name()} state-store-get", 
            spanBuilder = { builder ->
                trace?.let {
                    val linkContext = Tracing.contextFromTraceparent(trace)
                    builder.addLink(Span.fromContext(linkContext).getSpanContext())
                }
                builder
            }, 
            block = {}
        )
        return value
    }

    override fun put(key: Bytes, value: ByteArray?) {
        inner.put(key, wrap(value))
    }

    override fun all(): KeyValueIterator<Bytes, ByteArray> = TracingIterator(inner.all())
    override fun range(from: Bytes, to: Bytes): KeyValueIterator<Bytes, ByteArray> = TracingIterator(inner.range(from, to))

    inner class TracingIterator(private val iter: KeyValueIterator<Bytes, ByteArray>) : KeyValueIterator<Bytes, ByteArray> {
        override fun hasNext() = iter.hasNext()
        override fun next(): KeyValue<Bytes, ByteArray> {
            val next = iter.next()
            return KeyValue(next.key, unwrap(next.value).first)
        }
        override fun close() = iter.close()
        override fun peekNextKey(): Bytes = iter.peekNextKey()
        override fun remove() = iter.remove()
    }

    override fun name() = inner.name()
    override fun flush() = inner.flush()
    override fun close() = inner.close()
    override fun persistent() = inner.persistent()
    override fun isOpen() = inner.isOpen()
    override fun init(context: StateStoreContext, root: StateStore) = inner.init(context, root)
    override fun putIfAbsent(key: Bytes, value: ByteArray) = unwrap(inner.putIfAbsent(key, wrap(value)!!)).first
    override fun putAll(entries: List<KeyValue<Bytes, ByteArray>>) = inner.putAll(entries.map { KeyValue(it.key, wrap(it.value)) })
    override fun delete(key: Bytes) = inner.delete(key)
    override fun approximateNumEntries() = inner.approximateNumEntries()
    override fun getPosition(): Position = inner.position
}
