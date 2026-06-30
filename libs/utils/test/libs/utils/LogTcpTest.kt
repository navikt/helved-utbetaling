package libs.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogTcpTest {
    private val encoder = LogTcpEncoder().apply { start() }

    private fun event(msg: String, level: Level = Level.INFO): ILoggingEvent {
        val logger = (LoggerFactory.getLogger("test.Logger") as ch.qos.logback.classic.Logger)
        val ev = LoggingEvent(
            "test.Logger",
            logger,
            level,
            msg,
            null,
            null,
        )
        ev.threadName = "main"
        ev.timeStamp = 1719745200000
        ev.prepareForDeferredProcessing()
        return ev
    }

    @Test
    fun `produces valid JSON with required fields`() {
        val json = String(encoder.encode(event("hello")))
        assertTrue(json.contains("\"message\":\"hello\""))
        assertTrue(json.contains("\"level\":\"INFO\""))
        assertTrue(json.contains("\"@timestamp\""))
        assertTrue(json.contains("\"@version\":\"1\""))
        assertTrue(json.endsWith("\n"))
    }

    @Test
    fun `escapes special characters in message`() {
        val json = String(encoder.encode(event("line1\nline2\ttab\"quote")))
        assertTrue(json.contains("line1\\nline2\\ttab\\\"quote"))
    }

    @Test
    fun `includes MDC properties`() {
        MDC.put("callId", "abc-123")
        val ev = event("x")
        MDC.clear()
        val json = String(encoder.encode(ev))
        assertTrue(json.contains("\"callId\":\"abc-123\""))
    }

    @Test
    fun `remaps trace MDC keys`() {
        MDC.put("trace_id", "tid")
        val ev = event("x")
        MDC.clear()
        val json = String(encoder.encode(ev))
        assertTrue(json.contains("\"trace_id\":\"tid\""))
    }

    @Test
    fun `appends customFields`() {
        val enc = LogTcpEncoder().apply {
            customFields = """{"app":"utsjekk"}"""
            start()
        }
        val json = String(enc.encode(event("x")))
        assertTrue(json.contains("\"app\":\"utsjekk\""))
    }

    @Test
    fun `writes event to TCP socket`() {
        val server = java.net.ServerSocket(0)
        val port = server.localPort

        val appender = LogTcpAppender().apply {
            context = LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
            destination = "localhost:$port"
            encoder = LogTcpEncoder().apply { start() }
            start()
        }

        val client = server.accept()
        appender.doAppend(event("tcp-test"))

        val received = client.getInputStream().bufferedReader().readLine()
        assertTrue(received.contains("\"message\":\"tcp-test\""))

        appender.stop()
        server.close()
    }
}
