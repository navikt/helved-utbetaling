package abetal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.kafka.Topology
import libs.utils.appLog
import libs.utils.secureLog
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(
        factory = Netty,
        configure = {
            shutdownGracePeriod = 5000L
            shutdownTimeout = 50_000L
            connectors.add(EngineConnectorBuilder().apply {
                port = 8080
            })
        },
        module = Application::abetal,
    ).start(wait = true)
}

fun Application.abetal(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    topology: Topology = createTopology(kafka),
    awaitBeforeStart: (suspend () -> Unit) = { awaitUtsjekk(config.utsjekk) },
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val kafkaStarted = AtomicBoolean(false)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
    }

    routing {
        probes(kafka, prometheus, config.utsjekk, kafkaStarted)
    }

    // Production: wait for utsjekk to be ready before starting Kafka Streams,
    // so the saker-topic is populated before abetal starts consuming.
    launch {
        awaitBeforeStart()
        kafka.connect(topology, config.kafka, prometheus)
        kafkaStarted.set(true)
    }
}

internal suspend fun awaitUtsjekk(utsjekk: URL) {
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 2000
            connectTimeoutMillis = 1000
        }
    }

    client.use { client ->
        while (true) {
            val healthy = runCatching {
                client.get("$utsjekk/actuator/health").status.isSuccess()
            }.getOrDefault(false)

            if (healthy) {
                appLog.info("utsjekk is ready, starting Kafka Streams")
                return
            }

            appLog.info("Waiting for utsjekk to become ready...")
            delay(3.seconds)
        }
    }
}
