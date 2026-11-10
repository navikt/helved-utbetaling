package libs.kafka

import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.streams.errors.ErrorHandlerContext
import org.apache.kafka.streams.errors.ProcessingExceptionHandler
import org.apache.kafka.streams.processor.TaskId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProcessingExceptionHandlerTest {
    private val context = object : ErrorHandlerContext {
        override fun topic(): String? = null
        override fun partition() = -1
        override fun offset() = -1L
        override fun headers() = RecordHeaders()
        override fun processorNodeId() = "scheduler"
        override fun taskId() = TaskId(0, 0)
        override fun timestamp() = -1L
        override fun sourceRawKey(): ByteArray? = null
        override fun sourceRawValue(): ByteArray? = null
    }

    @Test
    fun `punctuation errors accept missing record without masking original exception`() {
        val error = IllegalStateException("Punctuation failed")
        assertEquals(ProcessingExceptionHandler.Response.fail().result(), ProcessAgainHandler().handleError(context, null, error).result())
        assertEquals(ProcessingExceptionHandler.Response.resume().result(), ProcessNextHandler().handleError(context, null, error).result())
    }
}
