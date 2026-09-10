package libs.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.read.ListAppender
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.test.Test
import kotlin.test.assertTrue

class LogTest {
    private val encoder = LogJsonEncoder().apply { start() }

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
    fun `appends customFields`() {
        val enc = LogJsonEncoder().apply {
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
            encoder = LogJsonEncoder().apply { start() }
            start()
        }

        val client = server.accept()
        appender.doAppend(event("tcp-test"))

        val received = client.getInputStream().bufferedReader().readLine()
        assertTrue(received.contains("\"message\":\"tcp-test\""))

        appender.stop()
        server.close()
    }

    @Test
    fun `error logs location publicly and exception securely`() {
        val (app, appender) = capture("appLog")
        val (secure, secureAppender) = capture("secureLog")
        val error = IllegalStateException("boom")

        try {
            Log.error("failed", error)
            assertEquals("failed", appender.list.single().formattedMessage)
            assertFalse(appender.list.single().throwableProxy != null)
            assertEquals("LogTest.kt", appender.list.single().mdcPropertyMap["location"]?.substringBefore(":"))
            val proxy = secureAppender.list.single().throwableProxy
            assertEquals(IllegalStateException::class.java.name, proxy.className)
            assertEquals("boom", proxy.message)
        } finally {
            app.detachAppender(appender)
            secure.detachAppender(secureAppender)
        }
    }

    @Test
    fun `error removes location from MDC`() {
        Log.error("failed", RuntimeException())
        assertEquals(null, MDC.get("location"))
    }

    private fun capture(name: String): Pair<Logger, ListAppender<ILoggingEvent>> {
        val logger = LoggerFactory.getLogger(name) as Logger
        return logger to ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }
}
