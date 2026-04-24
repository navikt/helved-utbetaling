package urskog

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.context
import libs.jdbc.concurrency.CoroutineDatasource
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.kafka.topology
import libs.mq.DefaultMQ
import libs.mq.MQ
import libs.utils.appLog
import libs.utils.secureLog
import models.erHelligdag
import java.time.LocalDate

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

    val jdbcCtx: CoroutineDatasource = Jdbc.initialize(config.jdbc).context()
    runBlocking {
        withContext(jdbcCtx) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    val oppdragProducer = OppdragMQProducer(config, mq, prometheus)
    val simuleringService = SimuleringService(config)
    val avstemProducer = AvstemmingMQProducer(config, mq)

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology {
            simulering(simuleringService)
            oppdrag(oppdragProducer, prometheus, jdbcCtx)
            avstemming(avstemProducer)
        }
    )

    // Ønsker ikke alarm for manglende kvittering på helligdager
    prometheus.gauge("is_helligdag", if (LocalDate.now().erHelligdag()) 1 else 0)

    val kvitteringConsumer = KvitteringMQConsumer(config, mq, kafka, jdbcCtx)

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


