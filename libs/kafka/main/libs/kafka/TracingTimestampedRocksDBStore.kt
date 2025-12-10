package libs.kafka

import io.opentelemetry.api.trace.Span
import libs.tracing.Tracing
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.processor.StateStore
import org.apache.kafka.streams.processor.StateStoreContext
import org.apache.kafka.streams.query.Position
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.Stores
import org.apache.kafka.streams.state.TimestampedBytesStore
import org.apache.kafka.streams.state.internals.RocksDBKeyValueBytesStoreSupplier
import org.apache.kafka.streams.state.internals.RocksDBTimestampedStore
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

private const val TRACE_CONTEXT_W3C_LEN = 55

class TracingTimestampedRocksDBStore(
    private val inner: RocksDBTimestampedStore,
) : KeyValueStore<Bytes, ByteArray>, TimestampedBytesStore {

    companion object {
        fun supplier(name: StateStoreName): KeyValueBytesStoreSupplier {
            return object: KeyValueBytesStoreSupplier {
                private val inner = Stores.persistentTimestampedKeyValueStore(name) as RocksDBKeyValueBytesStoreSupplier  
                override fun name() = inner.name()
                override fun get(): KeyValueStore<Bytes, ByteArray> = TracingTimestampedRocksDBStore(inner.get() as RocksDBTimestampedStore)
                override fun metricsScope(): String = "rocksdb"
            }
        }
    }

    private fun wrapValueWithTrace(value: ByteArray, trace: ByteArray): ByteArray {
        val combined = Arrays.copyOf(value, value.size + TRACE_CONTEXT_W3C_LEN)
        System.arraycopy(trace, 0, combined, value.size, TRACE_CONTEXT_W3C_LEN)
        return combined
    }

    private fun unwrapValueWithTrace(valueAndTrace: ByteArray?): Pair<ByteArray?, ByteArray?> {
        if (valueAndTrace == null) return null to null
        if (valueAndTrace.size < TRACE_CONTEXT_W3C_LEN) return valueAndTrace to null
        val payloadSize = valueAndTrace.size - TRACE_CONTEXT_W3C_LEN
        val payload = ByteArray(payloadSize)
        val traceSize = TRACE_CONTEXT_W3C_LEN
        val trace = ByteArray(TRACE_CONTEXT_W3C_LEN)
        System.arraycopy(valueAndTrace, 0, payload, 0, payloadSize)
        System.arraycopy(valueAndTrace, payloadSize, trace, 0, traceSize)
        return payload to trace
    }

    override fun get(key: Bytes): ByteArray? {
        kafkaLog.info("state store get, store name: ${name()}")
        val spanName = "${inner.name()} state-store-get"
        val startTs = Instant.now()

        when (val returned = inner[key]) {
            null -> {
                Tracing.startSpan(
                    name = spanName, 
                    spanBuilder = { builder -> 
                        builder.setParent(io.opentelemetry.context.Context.current())
                            .setStartTimestamp(startTs)
                    },
                    block = {}
                )
                return null
            }
            else -> {
                val (value, trace) = unwrapValueWithTrace(returned)
                val storedTraceparent = when (trace) {
                    null -> null // noisy logs for old data .also { kafkaLog.warn("no trace data in store, key $key, state store get, store name: ${name()}") }
                    else ->  String(trace, StandardCharsets.UTF_8)
                }
                Tracing.startSpan(
                    name = spanName, 
                    spanBuilder = { builder -> 
                        builder.setParent(io.opentelemetry.context.Context.current())
                        if (storedTraceparent != null) {
                            val linkContext = Tracing.contextFromTraceparent(storedTraceparent)
                            builder.addLink(Span.fromContext(linkContext).getSpanContext())
                        }
                        builder.setStartTimestamp(startTs)
                        builder
                    },
                    block = {}
                )
                return value
            }
        }
    }
    override fun put(key: Bytes, value: ByteArray) {
        kafkaLog.info("state store put, store name: ${name()}")
        val spanName = "${inner.name()} state-store-put"

        Tracing.startSpan(
            name = spanName, 
            spanBuilder = { builder -> 
                builder.setParent(io.opentelemetry.context.Context.current())
            },
            block = { _ ->
                val traceparent = Tracing.getTraceparent()
                if (traceparent == null) {
                    inner.put(key, value)
                    return@startSpan
                }
                val valueWithTrace = wrapValueWithTrace(value, traceparent.toByteArray(StandardCharsets.UTF_8))
                inner.put(key, valueWithTrace)
            }
        )
    }
    override fun putIfAbsent(key: Bytes, value: ByteArray): ByteArray? {
        kafkaLog.info("state store putIfAbsent, store name: ${name()}")
        val spanName = "${inner.name()} state-store-put"
        var returned: ByteArray? = null

        Tracing.startSpan(
            name = spanName, 
            spanBuilder = { builder -> 
                builder.setParent(io.opentelemetry.context.Context.current())
            },
            block = {
                val traceparent = Tracing.getTraceparent()
                if (traceparent == null) {
                    returned = inner.putIfAbsent(key, value)
                    return@startSpan
                }
                val valueWithTrace = wrapValueWithTrace(value, traceparent.toByteArray(StandardCharsets.UTF_8))
                returned = inner.putIfAbsent(key, valueWithTrace)
            }
        )
        return returned?.let {
            unwrapValueWithTrace(it).first
        }
    }

    override fun name(): String = inner.name()
    override fun init(context: StateStoreContext, root: StateStore) = inner.init(context, root)
    override fun flush() = inner.flush()
    override fun close() = inner.close()
    override fun persistent() = inner.persistent()
    override fun isOpen() = inner.isOpen()
    override fun range(from: Bytes, to: Bytes) = inner.range(from, to)
    override fun all() = inner.all()
    override fun approximateNumEntries() = inner.approximateNumEntries()
    override fun putAll(entries: List<KeyValue<Bytes, ByteArray>>) = inner.putAll(entries)
    override fun delete(key: Bytes) = inner.delete(key)
    override fun getPosition(): Position = inner.position
}

