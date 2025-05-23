package abetal

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.*
import libs.utils.*

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::abetal).start(wait = true)
}

fun Application.abetal(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    topology: Topology = createTopology(),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    monitor.subscribe(ApplicationStopping) { 
        kafka.close()
    }

    kafka.connect(topology, config.kafka, prometheus)

    val stateStore = kafka.getStore(Stores.utbetalinger)

    routing {
        probes(kafka, prometheus)
        api(stateStore)
    }
}


