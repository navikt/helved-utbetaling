package libs.tracing

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.*
import io.opentelemetry.context.*
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import libs.utils.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.collections.set

val traceLog = logger("trace")

/**
 * SDK wiring is owned by the OpenTelemetry java agent injected via NAIS
 * `observability.autoInstrumentation` in each app's nais.yml. Apps must NOT
 * call [init] in production -- doing so would create a second TracerProvider
 * competing with the agent's. The two-arg [init] / [shutdown] / [forceFlush]
 * are retained for the SDK integration test in this module only.
 *
 * All span / context helpers (startSpan, storeContext, restoreContext,
 * getTraceparent, getCurrentTraceId, propagateSpan, contextFromTraceparent)
 * read [openTelemetry] which falls back to [GlobalOpenTelemetry.get] when the
 * SDK has not been initialized in-process -- so they work transparently with
 * the agent.
 */
object Tracing {
    private const val tracerName = "helved-tracer"

    @Volatile
    private var openTelemetry: OpenTelemetry = GlobalOpenTelemetry.get()
    private var tracerProvider: SdkTracerProvider? = null

    val tracer: Tracer
        get() = openTelemetry.getTracer(tracerName)

    private val traceparents = ConcurrentHashMap<String, String>()

    @Synchronized
    fun init(serviceName: String, spanExporters: List<SpanExporter>) {
        if (tracerProvider != null) return

        val provider = SdkTracerProvider.builder()
            .setSampler(Sampler.parentBased(Sampler.alwaysOn()))
            .setResource(resource(serviceName))
            .apply {
                spanExporters
                    .map(::batchSpanProcessor)
                    .forEach(::addSpanProcessor)
            }
            .build()

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(provider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()

        tracerProvider = provider
    }

    fun forceFlush(): CompletableResultCode =
        tracerProvider?.forceFlush() ?: CompletableResultCode.ofSuccess()

    @Synchronized
    fun shutdown() {
        tracerProvider?.forceFlush()?.join(10, TimeUnit.SECONDS)
        (openTelemetry as? OpenTelemetrySdk)?.close()
        tracerProvider = null
        openTelemetry = GlobalOpenTelemetry.get()
    }

    fun storeContext(key: String) {
        getTraceparent()?.let { traceparents[key] = it } ?: traceLog.warn("No traceparent on context for key: $key")
    }

    fun restoreContext(key: String): Context {
        return traceparents[key]?.let { propagateSpan(it) } ?: Context.current()
    }

    fun getTraceparent(): String? {
        val ctx = Span.current().spanContext
        if (!ctx.isValid) return null
        val sampled = if (ctx.traceFlags.isSampled) "01" else "00"
        return "00-${ctx.traceId}-${ctx.spanId}-$sampled"
    }

    fun getTraceparent(ctx: SpanContext): String? {
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

    private val traceContextGetter = object : TextMapGetter<String> {
        override fun keys(carrier: String): Iterable<String> = Collections.singletonList("traceparent")
        override fun get(carrier: String?, key: String): String? = if ("traceparent" == key) carrier else null
    }

    fun contextFromTraceparent(traceparent: String): Context {
        return openTelemetry
            .getPropagators()
            .textMapPropagator
            .extract(Context.current(), traceparent, traceContextGetter)
    }

    private fun batchSpanProcessor(spanExporter: SpanExporter): BatchSpanProcessor =
        BatchSpanProcessor.builder(spanExporter)
            .setMaxExportBatchSize(512)
            .setScheduleDelay(5, TimeUnit.SECONDS)
            .build()

    private fun resource(serviceName: String): Resource =
        Resource.getDefault().merge(
            Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), serviceName)
            )
        )
}
