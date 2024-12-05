package abetal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.MeterRegistry
import libs.utils.*
import libs.kafka.Streams
import libs.kafka.KafkaStreams
import libs.kafka.Topology
import libs.kafka.topology

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::abetal).start(wait = true)
}

fun Application.abetal(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    monitor.subscribe(ApplicationStopping) { 
        kafka.close()
    }

    val utbetProducer = kafka.createProducer(config.kafka, Topics.utbetalinger)

    kafka.connect(
        topology = createTopology(),
        config = config.kafka,
        registry = prometheus,
    )

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    routing {
        utbetalingRoute(utbetProducer)
    }
}


