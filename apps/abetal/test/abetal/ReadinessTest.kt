package abetal

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libs.kafka.StreamsMock
import libs.kafka.StateStore
import libs.kafka.Store
import libs.kafka.Streams
import libs.kafka.StreamsConfig
import libs.kafka.Topology
import libs.kafka.TopologyVisulizer
import libs.ktor.port
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class ReadinessTest {

    @Test
    fun `utsjekk readiness 200 immediately starter streams uten unødvendig venting`() {
        ReadinessServer(listOf(HttpStatusCode.OK)).use { utsjekk ->
            val streams = RecordingStreams()

            val duration = startAbetal(
                config = testConfig(utsjekk.url, readinessMaxWaitSeconds = 5),
                streams = streams,
            )

            assertTrue(streams.awaitStarted(1.seconds), "Kafka Streams did not start")
            assertTrue(duration < 1.seconds, "Expected startup within 1s, got $duration")
        }
    }

    @Test
    fun `utsjekk readiness 200 etter tre retries starter streams med backoff`() {
        ReadinessServer(
            listOf(
                HttpStatusCode.ServiceUnavailable,
                HttpStatusCode.ServiceUnavailable,
                HttpStatusCode.ServiceUnavailable,
                HttpStatusCode.OK,
            )
        ).use { utsjekk ->
            val streams = RecordingStreams()

            val duration = startAbetal(
                config = testConfig(utsjekk.url, readinessMaxWaitSeconds = 10),
                streams = streams,
            )

            assertTrue(streams.awaitStarted(1.seconds), "Kafka Streams did not start")
            assertTrue(
                duration.inWholeMilliseconds in 3_000..8_000,
                "Expected startup after exponential backoff, got $duration"
            )
        }
    }

    @Test
    fun `utsjekk readiness alltid 503 og max wait 5 sek starter streams degraded og logger feil`() {
        ReadinessServer(listOf(HttpStatusCode.ServiceUnavailable)).use { utsjekk ->
            val streams = RecordingStreams()

            withAppLogCapture { logEvents ->
                val duration = startAbetal(
                    config = testConfig(utsjekk.url, readinessMaxWaitSeconds = 5),
                    streams = streams,
                )

                assertTrue(streams.awaitStarted(1.seconds), "Kafka Streams did not start")
                assertTrue(
                    duration.inWholeMilliseconds in 4_500..8_000,
                    "Expected degraded startup around 5s, got $duration"
                )
                assertTrue(
                    logEvents.any {
                        it.level.levelStr == "ERROR" &&
                            it.formattedMessage.contains("Utsjekk readiness did not succeed within 5s")
                    },
                    "Expected degraded startup error log"
                )
            }
        }
    }
}

private fun startAbetal(
    config: Config,
    streams: RecordingStreams,
): Duration {
    val server = embeddedServer(Netty, port = 0) {
        abetal(
            config = config,
            kafka = streams,
            topology = Topology(),
            startupConfigValidator = {},
        )
    }

    val mark = TimeSource.Monotonic.markNow()

    try {
        server.start(wait = false)
        assertTrue(streams.awaitStarted((config.readinessMaxWaitSeconds + 2).seconds), "Kafka Streams did not start")
        return mark.elapsedNow()
    } finally {
        server.stop(0, 0)
    }
}

private fun testConfig(
    utsjekk: URL,
    readinessMaxWaitSeconds: Long,
): Config = Config(
    utsjekk = utsjekk,
    readinessMaxWaitSeconds = readinessMaxWaitSeconds,
    kafka = StreamsMock().config,
)

private inline fun withAppLogCapture(block: (List<ILoggingEvent>) -> Unit) {
    val logger = LoggerFactory.getLogger("appLog") as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }

    logger.addAppender(appender)

    try {
        block(appender.list)
    } finally {
        logger.detachAppender(appender)
        appender.stop()
    }
}

private class ReadinessServer(statuses: List<HttpStatusCode>) : AutoCloseable {
    private val calls = AtomicInteger(0)
    private val server = embeddedServer(Netty, port = 0) {
        routing {
            get("/actuator/ready") {
                val index = calls.getAndIncrement().coerceAtMost(statuses.lastIndex)
                call.respond(statuses[index])
            }
        }
    }.apply { start(wait = false) }

    val url = URI("http://localhost:${awaitPort(server)}").toURL()

    override fun close() {
        server.stop(0, 0)
    }
}

private fun awaitPort(server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>): Int = runBlocking {
    withTimeout(5.seconds) {
        var port = 0
        while (port <= 0) {
            port = runCatching { server.engine.port }.getOrNull() ?: 0
            if (port <= 0) delay(10)
        }

        port
    }
}

private class RecordingStreams : Streams {
    private val started = CountDownLatch(1)

    override fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry) {
        error("connect should not be called in readiness tests")
    }

    override fun ready(): Boolean = false

    override fun live(): Boolean = true

    override fun visulize(): TopologyVisulizer = TopologyVisulizer(org.apache.kafka.streams.Topology())

    override fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology) {}

    override fun <K : Any, V : Any> getStore(store: Store<K, V>): StateStore<K, V> {
        error("getStore should not be called in readiness tests")
    }

    override fun close(gracefulMillis: Long) {}

    override fun close() {}

    override fun start(
        topology: Topology,
        config: StreamsConfig,
        registry: MeterRegistry,
        awaitReady: () -> Boolean,
    ) {
        started.countDown()
    }

    fun awaitStarted(timeout: Duration): Boolean = started.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}
