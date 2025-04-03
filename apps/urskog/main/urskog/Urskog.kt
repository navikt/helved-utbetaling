package urskog

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
import libs.mq.MQ
import libs.mq.DefaultMQ

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::urskog).start(wait = true)
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

    val oppdragProducer = OppdragMQProducer(config, mq)
    val simuleringService = SimuleringService(config)

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology {
            simulering(simuleringService)
            oppdrag(oppdragProducer)
            kvittering(prometheus)
        }
    )

    val kvitteringQueueProducer = kafka.createProducer(config.kafka, Topics.kvitteringQueue)
    val kvitteringConsumer = KvitteringMQConsumer(config, kvitteringQueueProducer, mq)

    monitor.subscribe(ApplicationStarted) {
        kvitteringConsumer.start()
    }

    monitor.subscribe(ApplicationStopping) { 
        kafka.close()
        kvitteringConsumer.close()
        kvitteringQueueProducer.close() // necessary?
    }

    routing {
        probes(kafka, prometheus)
    }
}


