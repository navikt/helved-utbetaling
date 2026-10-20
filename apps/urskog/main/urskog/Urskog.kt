package urskog

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
import libs.utils.Log
import libs.utils.erHelligdag
import org.http4k.core.HttpHandler
import org.http4k.core.then
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.time.Duration
import java.time.LocalDate

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val app = urskog()
    val server = app.handler
        .asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50))))
        .start()

    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            app.close()
        } finally {
            server.stop()
        }
    })

    app.start()
    server.block()
}

class Urskog(
    val handler: HttpHandler,
    private val kvitteringConsumer: KvitteringMQConsumer,
    private val closeables: List<AutoCloseable>
) : AutoCloseable {
    fun start() = kvitteringConsumer.start()

    override fun close() = closeables.forEach { it.close() }
}

fun urskog(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    mq: MQ = DefaultMQ(config.mq)
): Urskog {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val logbackMetrics = LogbackMetrics().also { it.bindTo(prometheus) }

    val jdbcCtx: CoroutineDatasource = Jdbc.initialize(config.jdbc).context()
    runBlocking {
        withContext(jdbcCtx) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    val oppdragProducer = OppdragMQProducer(config, mq, prometheus)
    val avstemProducer = AvstemmingMQProducer(config, mq)

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology {
            oppdrag(oppdragProducer, prometheus, jdbcCtx)
            avstemming(avstemProducer)
            utbetalingToSak(jdbcCtx)
        }
    )

    // Ønsker ikke alarm for manglende kvittering på helligdager
    prometheus.gauge("is_helligdag", if (LocalDate.now().erHelligdag()) 1 else 0)

    val kvitteringConsumer = KvitteringMQConsumer(config, mq, kafka, jdbcCtx)

    val handler = ServerFilters.MicrometerMetrics.RequestTimer(prometheus)
        .then(ServerFilters.MicrometerMetrics.RequestCounter(prometheus))
        .then(probes(kafka, prometheus))

    return Urskog(
        handler,
        kvitteringConsumer,
        listOfNotNull(
            kvitteringConsumer,
            kafka,
            jdbcCtx.datasource as? AutoCloseable,
            logbackMetrics,
            AutoCloseable { prometheus.close() }
        )
    )
}
