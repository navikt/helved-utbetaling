package urskog

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.LocalDate
import libs.kafka.*
import libs.utils.*
import libs.mq.MQ
import libs.mq.DefaultMQ
import models.erHelligdag

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
        module = Application::urskog,
    ).start(wait = true)
}

fun Application.urskog(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    mq: MQ = DefaultMQ(config.mq),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    val oppdragProducer = OppdragMQProducer(config, mq, prometheus)
    val simuleringService = SimuleringService(config)
    val avstemProducer = AvstemmingMQProducer(config, mq)

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology {
            simulering(simuleringService)
            oppdrag(oppdragProducer, prometheus)
            avstemming(avstemProducer)
        }
    )

    // Ønsker ikke alarm for manglende kvittering på helligdager
    prometheus.gauge("is_helligdag", if (LocalDate.now().erHelligdag()) 1 else 0)

    val kvitteringConsumer = KvitteringMQConsumer(config, mq, kafka)

    monitor.subscribe(ApplicationStarted) {
        kvitteringConsumer.start()
    }

    monitor.subscribe(ApplicationStopping) { 
        kafka.close()
        kvitteringConsumer.close()
    }

    routing {
        probes(kafka, prometheus)
    }
}


