package libs.tracing

import libs.utils.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.*
import io.opentelemetry.context.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

val traceLog = logger("trace")

object Tracing {
    val tracer: Tracer = GlobalOpenTelemetry.getTracer("helved-tracer")

    private val traceparents = ConcurrentHashMap<String, String>()

    fun storeContext(key: String) {
        getTraceparent()?.let { traceparents[key] = it } ?: traceLog.warn("No traceparent on context for key: $key")
    }

    fun restoreContext(key: String): Context {
        return traceparents[key]?.let { propagateSpan(it) } ?: Context.current()
    }

    private fun getTraceparent(): String? {
        val ctx = Span.current().spanContext
        if (!ctx.isValid) return null
        val sampled = if (ctx.traceFlags.isSampled) "01" else "00"
        return "00-${ctx.traceId}-${ctx.spanId}-$sampled"
    }

    private fun propagateSpan(traceparent: String): Context {
        val split = traceparent.split("-")
        if (split.size < 4) {
            traceLog.warn("Invalid traceparent: $traceparent")
            return Context.current()
        }
        val traceId = split[1]
        val parentSpanId = split[2]
        val traceFlags = TraceFlags.getSampled()
        val traceState = TraceState.getDefault()
        val spanCtx = SpanContext.createFromRemoteParent(traceId, parentSpanId, traceFlags, traceState)
        return Context.current().with(Span.wrap(spanCtx))
    }

    /**
     * Start a new span and append it to the context
     */
    fun startSpan(
        name: String,
        spanBuilder: (SpanBuilder) -> SpanBuilder,
        block: (Span) -> Unit
    ) {
        val span = spanBuilder(tracer.spanBuilder(name)).startSpan()
        try {
            span.makeCurrent().use {
                block(span)
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, "Error: ${e.message}")
            throw e
        } finally {
            span.end()
        }
    }

    /**
     * When passing spans across execution boundaries, use context propagation
     */
    fun propagateSpan(): Context {
        val span = Span.current()
        return Context.current().with(span)
    }

    fun getCurrentTraceId(): String? {
        val traceId = Span.current().spanContext.traceId
        return traceId
    }
}
