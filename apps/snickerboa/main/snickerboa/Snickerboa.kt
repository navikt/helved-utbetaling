package snickerboa

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
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.utils.appLog
import libs.utils.secureLog
import models.ApiError
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.lens.LensFailure
import org.http4k.routing.routes
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.time.Duration

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val config = Config()
    val app = snickerboa(config)
    val server = app.handler.asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50)))).start()

    Runtime.getRuntime().addShutdownHook(Thread {
        app.close()
        server.stop()
    })

    server.block()
}

class Snickerboa(
    val handler: HttpHandler,
    private val closeables: List<AutoCloseable>,
) : AutoCloseable {
    override fun close() = closeables.forEach { it.close() }
}

fun snickerboa(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    factory: KafkaFactory = object : KafkaFactory {},
): Snickerboa {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    LogbackMetrics().bindTo(prometheus)

    val producers = UtbetalingProducers.create(factory, config.kafka)
    val correlator = RequestReplyCorrelator(producers)

    val statusKafkaConsumer = factory.createConsumer(config.kafka, Topics.status)
    val dryrunAapConsumer = factory.createConsumer(config.kafka, Topics.dryrunAap)
    val dryrunDpConsumer = factory.createConsumer(config.kafka, Topics.dryrunDp)
    val dryrunTsConsumer = factory.createConsumer(config.kafka, Topics.dryrunTs)
    val dryrunTpConsumer = factory.createConsumer(config.kafka, Topics.dryrunTp)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val statusJob = scope.launch { statusConsumer(correlator, statusKafkaConsumer) }
    val aapJob = scope.launch { dryrunConsumer(correlator, dryrunAapConsumer) }
    val dpJob = scope.launch { dryrunConsumer(correlator, dryrunDpConsumer) }
    val tsJob = scope.launch { dryrunConsumer(correlator, dryrunTsConsumer) }
    val tpJob = scope.launch { dryrunConsumer(correlator, dryrunTpConsumer) }

    val handler = errorFilter
        .then(ServerFilters.MicrometerMetrics.RequestTimer(prometheus))
        .then(ServerFilters.MicrometerMetrics.RequestCounter(prometheus))
        .then(routes(
            probes(prometheus),
            snickerboaRoutes(correlator),
        ))

    val closeables = listOf(
        AutoCloseable { producers.close() },
        AutoCloseable { statusJob.cancelJob() },
        AutoCloseable { aapJob.cancelJob() },
        AutoCloseable { dpJob.cancelJob() },
        AutoCloseable { tsJob.cancelJob() },
        AutoCloseable { tpJob.cancelJob() },
        AutoCloseable { kafka.close() },
    )

    return Snickerboa(handler, closeables)
}

private val apiErrorLens = KotlinxJson.autoBody<ApiError>().toLens()

private val errorFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: ApiError) {
            Response(Status(e.statusCode, "")).with(apiErrorLens of e)
        } catch (e: LensFailure) {
            val msg = "Påkrevd felt mangler eller er null: ${e.message}"
            appLog.warn(msg, e)
            Response(Status.BAD_REQUEST).body(msg)
        } catch (e: Throwable) {
            val msg = "Uhåndtert feil: ${e.message}"
            appLog.warn(msg, e)
            Response(Status.INTERNAL_SERVER_ERROR).body(msg)
        }
    }
}

fun Job.cancelJob() {
    if (!this.isCompleted) runBlocking(Dispatchers.IO) {
        appLog.info("Job cancelled")
        this@cancelJob.cancelAndJoin()
    }
}
