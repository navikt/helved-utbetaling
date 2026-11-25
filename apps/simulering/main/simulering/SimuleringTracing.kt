package simulering

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import org.slf4j.MDC

object SimuleringTracing {
    private val headerGetter = TextMapGetter<Map<String, String>> { headers, key -> headers?.get(key) }

    fun <T> kafka(headers: Map<String, String>, block: () -> T): T {
        // A missing/invalid Kafka header must not inherit another record's thread-local trace.
        val parent = W3CTraceContextPropagator.getInstance().extract(Context.root(), headers, headerGetter)
        return span("simulering.kafka", parent, SpanKind.CONSUMER, block)
    }

    fun <T> span(name: String, parent: Context, kind: SpanKind = SpanKind.INTERNAL, block: () -> T): T {
        val span = GlobalOpenTelemetry.getTracer("simulering").spanBuilder(name).setParent(parent).setSpanKind(kind).startSpan()
        try {
            return span.makeCurrent().use { withLogContext(block) }
        } catch (e: Exception) {
            // Trace backends are not secure logs; do not export payload-bearing exception messages.
            span.setStatus(StatusCode.ERROR)
            throw e
        } finally {
            span.end()
        }
    }

    fun <T> withLogContext(block: () -> T): T {
        val previousTraceId = MDC.get("trace_id")
        val previousSpanId = MDC.get("span_id")
        val span = Span.current().spanContext
        setMdc("trace_id", span.traceId.takeIf { span.isValid })
        setMdc("span_id", span.spanId.takeIf { span.isValid })
        try {
            return block()
        } finally {
            setMdc("trace_id", previousTraceId)
            setMdc("span_id", previousSpanId)
        }
    }

    private fun setMdc(key: String, value: String?) {
        if (value == null) MDC.remove(key) else MDC.put(key, value)
    }
}
