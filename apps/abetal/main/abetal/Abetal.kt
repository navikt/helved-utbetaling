package abetal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.kafka.Topology
import libs.tracing.Tracing
import libs.utils.appLog
import libs.utils.secureLog
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
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
    prometheus: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
    metrics: Metrics = Metrics(prometheus),
    topology: Topology = createTopology(kafka, metrics),
    startupConfigValidator: suspend (Config) -> Unit = ::validateStartupConfigOrExit,
    awaitUtsjekkReady: suspend (Config) -> Boolean = { cfg ->
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build()
        awaitUtsjekkStartupReadiness(httpClient, cfg)
    },
    isUtsjekkReadyCheck: (Config) -> Boolean = { cfg ->
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build()
        isUtsjekkReady(httpClient, cfg)
    },
) {
    Tracing.init("abetal")

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
        Tracing.shutdown()
        kafka.close()
    }

    routing {
        probes(kafka, prometheus)
    }

    runBlocking {
        startupConfigValidator(config)
    }

    val utsjekkReadyAtStartup = runBlocking {
        awaitUtsjekkReady(config)
    }

    if (!utsjekkReadyAtStartup) {
        appLog.error(
            "Utsjekk readiness did not succeed within ${config.readinessMaxWaitSeconds}s. " +
                "Starting Kafka Streams in degraded mode."
        )
    }

    val startInDegradedMode = AtomicBoolean(!utsjekkReadyAtStartup)

    // Starts Kafka Streams paused, resumes when utsjekk is ready.
    // Pauses again if utsjekk becomes unavailable.
    kafka.start(topology, config.kafka, prometheus) {
        startInDegradedMode.getAndSet(false) || isUtsjekkReadyCheck(config)
    }
}

private suspend fun awaitUtsjekkStartupReadiness(
    httpClient: HttpClient,
    config: Config,
): Boolean {
    var backoff = 1.seconds

    return withTimeoutOrNull(config.readinessMaxWaitSeconds.seconds) {
        while (!isUtsjekkReady(httpClient, config)) {
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30.seconds)
        }

        true
    } ?: false
}

private fun isUtsjekkReady(
    httpClient: HttpClient,
    config: Config,
): Boolean = runCatching {
    val request = HttpRequest.newBuilder()
        .uri(URI("${config.utsjekk}/actuator/ready"))
        .timeout(Duration.ofSeconds(2))
        .GET()
        .build()

    httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..299
}.getOrDefault(false)
