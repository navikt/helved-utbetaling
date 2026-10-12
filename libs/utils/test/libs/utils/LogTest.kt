package libs.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.Appender
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.status.Status
import ch.qos.logback.core.util.Duration
import net.logstash.logback.appender.LogstashTcpSocketAppender
import net.logstash.logback.appender.listener.TcpAppenderListener
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.test.Test
import kotlin.test.assertTrue

class LogTest {
    private val encoder = LogJsonEncoder().apply { start() }

    private fun event(msg: String, level: Level = Level.INFO): ILoggingEvent {
        val logger = (LoggerFactory.getLogger("test.Logger") as Logger)
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
    fun `writes queued event with MDC and custom fields before stopping`() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5_000
            val appender = tcpAppender(server.localPort)
            try {
                server.accept().use { client ->
                    client.soTimeout = 5_000
                    MDC.putCloseable("callId", "tcp-call").use {
                        appender.doAppend(event("tcp-test"))
                    }
                    appender.stop()

                    val received = client.getInputStream().bufferedReader().readLine()
                    assertTrue(received.contains("\"message\":\"tcp-test\""))
                    assertTrue(received.contains("\"callId\":\"tcp-call\""))
                    assertTrue(received.contains("\"app\":\"test\""))
                    assertFalse(appender.isStarted)
                }
            } finally {
                appender.stop()
            }
        }
    }

    @Test
    fun `recovers when receiver is unavailable at startup`() {
        val port = ServerSocket(0).use { it.localPort }
        val failed = CountDownLatch(1)
        val listener = object : TcpAppenderListener<ILoggingEvent> {
            override fun connectionFailed(
                appender: Appender<ILoggingEvent>,
                address: InetSocketAddress,
                reason: Throwable
            ) {
                failed.countDown()
            }
        }
        val appender = tcpAppender(port, listener)
        try {
            appender.doAppend(event("queued-during-outage"))
            assertTrue(failed.await(5, TimeUnit.SECONDS), "Expected an initial connection failure")

            ServerSocket(port).use { server ->
                server.soTimeout = 5_000
                server.accept().use { client ->
                    client.soTimeout = 5_000
                    val received = client.getInputStream().bufferedReader().readLine()
                    assertTrue(received.contains("\"message\":\"queued-during-outage\""))
                }
            }
        } finally {
            appender.stop()
        }
    }

    @Test
    fun `reconnects after receiver disconnects`() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5_000
            val closed = CountDownLatch(1)
            val listener = object : TcpAppenderListener<ILoggingEvent> {
                override fun connectionClosed(appender: Appender<ILoggingEvent>, socket: Socket) {
                    closed.countDown()
                }
            }
            val appender = tcpAppender(server.localPort, listener)
            try {
                server.accept().use { client ->
                    client.soTimeout = 5_000
                    appender.doAppend(event("before-disconnect"))
                    assertTrue(client.getInputStream().bufferedReader().readLine().contains("before-disconnect"))
                    client.setSoLinger(true, 0)
                }
                assertTrue(closed.await(5, TimeUnit.SECONDS), "Expected the broken connection to be detected")

                server.accept().use { client ->
                    client.soTimeout = 5_000
                    appender.doAppend(event("after-reconnect"))
                    assertTrue(client.getInputStream().bufferedReader().readLine().contains("after-reconnect"))
                }
            } finally {
                appender.stop()
            }
        }
    }

    @Test
    fun `application configurations ship secure and audit logs without console leakage`() {
        val configs = File("../../apps").listFiles().orEmpty()
            .map { File(it, "main/logback.xml") }
            .filter { it.isFile }
        assertTrue(configs.isNotEmpty(), "Expected application Logback configurations")

        for (config in configs) ServerSocket(0).use { server ->
            server.soTimeout = 5_000
            val context = LoggerContext().apply {
                name = config.parentFile.parentFile.name
                mdcAdapter = (LoggerFactory.getILoggerFactory() as LoggerContext).mdcAdapter
                start()
            }
            try {
                val xml = config.readText()
                    .replace("team-logs.nais-system:5170", "localhost:${server.localPort}")
                JoranConfigurator().apply { this.context = context }
                    .doConfigure(xml.byteInputStream())
                val problems = context.statusManager.copyOfStatusList.filter { it.level >= Status.WARN }
                assertTrue(problems.isEmpty(), "${context.name}: $problems")
                val console = ListAppender<ILoggingEvent>().apply {
                    this.context = context
                    start()
                }
                context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(console)

                server.accept().use { first ->
                    server.accept().use { second ->
                        first.soTimeout = 5_000
                        second.soTimeout = 5_000
                        MDC.putCloseable("callId", "config-call").use {
                            context.getLogger("secureLog").info("secure-only")
                            context.getLogger("audit").info("audit-only")
                        }
                        context.stop()

                        val messages = listOf(first, second).map {
                            it.getInputStream().bufferedReader().readLine()
                        }
                        assertTrue(messages.any { it.contains("\"message\":\"secure-only\"") })
                        val audit = messages.single { it.contains("\"message\":\"audit-only\"") }
                        assertTrue(audit.contains("\"team_audit\":\"helved\""))
                        assertTrue(messages.all { it.contains("\"callId\":\"config-call\"") })
                        assertTrue(messages.all { it.contains("\"google_cloud_project\":") })
                        assertTrue(console.list.isEmpty(), "${context.name}: private logs reached root logger")
                    }
                }
            } finally {
                context.stop()
            }
        }
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

    private fun tcpAppender(
        port: Int,
        listener: TcpAppenderListener<ILoggingEvent>? = null
    ): LogstashTcpSocketAppender = LogstashTcpSocketAppender().apply {
        context = LoggerFactory.getILoggerFactory() as LoggerContext
        name = "tcp-test"
        addDestination("localhost:$port")
        reconnectionDelay = Duration.buildByMilliseconds(100.0)
        connectionTimeout = Duration.buildBySeconds(1.0)
        shutdownGracePeriod = Duration.buildBySeconds(1.0)
        encoder = LogJsonEncoder().apply {
            customFields = """{"app":"test"}"""
            start()
        }
        listener?.let { addListener(it) }
        start()
    }
}
