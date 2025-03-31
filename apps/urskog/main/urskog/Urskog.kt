package urskog

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import kotlinx.coroutines.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.*
import libs.utils.*
import libs.mq.MQ
import libs.mq.DefaultMQ
import models.*
import org.apache.kafka.clients.producer.Producer
import java.util.UUID

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::urskog).start(wait = true)
}

class KafkaStartedEvent

fun Application.urskog(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    mq: MQ = DefaultMQ(config.mq),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val oppdragProducer = OppdragMQProducer(config, mq)
    val avstemProducer = AvstemmingMQProducer(config, mq)
    val simuleringService = SimuleringService(config)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    kafka.connect(
        topology = createTopology(oppdragProducer, avstemProducer, simuleringService, prometheus),
        config = config.kafka,
        registry = prometheus,
    )

    val kvitteringQueueProducer = kafka.createProducer(config.kafka, Topics.kvitteringQueue) 
    val kvitteringConsumer = KvitteringMQConsumer(config, kvitteringQueueProducer, mq)

    monitor.subscribe(ApplicationStopping) { 
        kvitteringConsumer.start()
        kafka.close()
        kvitteringConsumer.close()
    }

    monitor.subscribe(ApplicationStarted) {
        kvitteringConsumer.start()
    }

    routing {
        probes(kafka, prometheus)
    }
}


