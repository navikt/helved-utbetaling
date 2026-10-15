package statistikkern

import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libs.kafka.KafkaFactory
import libs.kafka.OffsetResetPolicy
import libs.utils.Log
import org.http4k.core.HttpHandler
import org.http4k.core.then
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.routing.routes
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.time.Duration

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val app = statistikkern()
    val server = app.handler
        .asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50))))
        .start()

    Runtime.getRuntime().addShutdownHook(Thread {
        app.close()
        server.stop()
    })

    server.block()
}

class Statistikkern(
    val handler: HttpHandler,
    private val closeables: List<AutoCloseable>
) : AutoCloseable {
    override fun close() = closeables.forEach { it.close() }
}

fun statistikkern(
    config: Config = Config(),
    bqService: BigQueryService = BigQueryService(),
    factory: KafkaFactory = object : KafkaFactory {}
): Statistikkern {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    LogbackMetrics().bindTo(prometheus)

    val utbetalingConsumer = factory.createConsumer(config.kafka, Topics.utbetalinger, resetPolicy = OffsetResetPolicy.latest)
    val statusConsumer = factory.createConsumer(config.kafka, Topics.status, resetPolicy = OffsetResetPolicy.latest)
    val oppdragConsumer = factory.createConsumer(config.kafka, Topics.oppdrag, resetPolicy = OffsetResetPolicy.latest)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val utbetalingJob = scope.launch { utbetalingConsumer(bqService, utbetalingConsumer) }
    val statusJob = scope.launch { statusConsumer(bqService, statusConsumer) }
    val oppdragJob = scope.launch { oppdragConsumer(bqService, oppdragConsumer) }

    val handler = ServerFilters.MicrometerMetrics.RequestTimer(prometheus)
        .then(ServerFilters.MicrometerMetrics.RequestCounter(prometheus))
        .then(routes(probes(prometheus)))

    return Statistikkern(
        handler,
        listOf(
            AutoCloseable { utbetalingJob.cancelJob() },
            AutoCloseable { statusJob.cancelJob() },
            AutoCloseable { oppdragJob.cancelJob() },
            AutoCloseable { utbetalingConsumer.close() },
            AutoCloseable { statusConsumer.close() },
            AutoCloseable { oppdragConsumer.close() }
        )
    )
}

fun Job.cancelJob() {
    if (!this.isCompleted) runBlocking(Dispatchers.IO) {
        Log.info("Job cancelled")
        this@cancelJob.cancelAndJoin()
    }
}
