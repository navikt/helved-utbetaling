package overfør

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.*
import libs.mq.MQ
import libs.utils.*

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::overfør).start(wait = true)
}

fun Application.overfør(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val mq = MQ(config.mq)
    val oppdragProducer = OppdragProducer(config.oppdrag, mq)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    monitor.subscribe(ApplicationStopping) { 
        kafka.close()
    }

    kafka.connect(
        topology = createTopology(oppdragProducer),
        config = config.kafka,
        registry = prometheus,
    )

    routing {
        probes(kafka, prometheus)
    }
}


