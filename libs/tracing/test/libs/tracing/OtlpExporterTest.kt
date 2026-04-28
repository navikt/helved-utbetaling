package libs.tracing

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OtlpExporterTest {
    @Test
    fun `eksporterer span med batch processor`() {
        val exporter = RecordingSpanExporter()

        Tracing.init("tracing-test", listOf(exporter))

        Tracing.startSpan("otlp-test", { it }) { }

        assertTrue(Tracing.forceFlush().join(5, TimeUnit.SECONDS).isSuccess, "forceFlush should succeed")
        assertEquals(listOf("otlp-test"), exporter.exportedSpanNames())
    }

    @AfterTest
    fun tearDown() {
        Tracing.shutdown()
    }
}

private class RecordingSpanExporter : SpanExporter {
    private val spans = mutableListOf<SpanData>()

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        synchronized(this.spans) {
            this.spans += spans
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    fun exportedSpanNames(): List<String> = synchronized(spans) {
        spans.map(SpanData::getName)
    }
}
