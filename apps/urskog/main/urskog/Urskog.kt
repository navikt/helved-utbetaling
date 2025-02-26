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
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val oppdragProducer = OppdragMQProducer(config)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    kafka.connect(
        topology = createTopology(oppdragProducer),
        config = config.kafka,
        registry = prometheus,
    )

    val kvitteringProducer = kafka.createProducer(config.kafka, Topics.kvittering) 
    val keystore = kafka.getStore(Stores.keystore)  
    val kvitteringConsumer = KvitteringMQConsumer(config, kvitteringProducer, keystore)

    monitor.subscribe(ApplicationStopping) { 
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


