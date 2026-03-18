package statistikkern

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libs.kafka.KafkaFactory
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.utils.appLog
import libs.utils.secureLog

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
        module = Application::statistikkern,
    ).start(wait = true)
}

fun Application.statistikkern(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    bqService: BigQueryService = BigQueryService(),
    factory: KafkaFactory = object : KafkaFactory {}
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
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

    val utbetalingConsumer = factory.createConsumer(config.kafka, Topics.utbetalinger)
    val statusConsumer = factory.createConsumer(config.kafka, Topics.status)
    val oppdragConsumer = factory.createConsumer(config.kafka, Topics.oppdrag)

    val utbetalingJob = launch { utbetalingConsumer(bqService, utbetalingConsumer) }
    val statusJob = launch { statusConsumer(bqService, statusConsumer) }
    val oppdragJob = launch { oppdragConsumer(bqService, oppdragConsumer) }

    routing {
        probes(prometheus)
    }

    monitor.subscribe(ApplicationStopping) {
        utbetalingJob.cancelJob()
        statusJob.cancelJob()
        oppdragJob.cancelJob()
        kafka.close()
    }
}

fun Job.cancelJob() {
    if (!this.isCompleted) runBlocking(Dispatchers.IO) {
        appLog.info("Job cancelled")
        this@cancelJob.cancelAndJoin()
    }
}