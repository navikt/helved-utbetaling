package snickerboa

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libs.kafka.KafkaFactory
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.utils.appLog
import libs.utils.secureLog
import models.ApiError

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
        module = Application::snickerboa,
    ).start(wait = true)
}

fun Application.snickerboa(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    factory: KafkaFactory = object : KafkaFactory {}
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ApiError -> call.respond(HttpStatusCode.fromValue(cause.statusCode), cause)
                is BadRequestException -> {
                    val msg = "Påkrevd felt mangler eller er null: ${cause.message}"
                    appLog.warn(msg, cause)
                    call.respond(HttpStatusCode.BadRequest, msg)
                }

                else -> {
                    val msg = "Uhåndtert feil: ${cause.message}"
                    appLog.warn(msg, cause)
                    call.respond(HttpStatusCode.InternalServerError, msg)
                }
            }
        }
    }

    val producers = UtbetalingProducers.create(factory, config.kafka)
    val correlator = RequestReplyCorrelator(producers)

    val statusKafkaConsumer = factory.createConsumer(config.kafka, Topics.status)
    val dryrunAapConsumer = factory.createConsumer(config.kafka, Topics.dryrunAap)
    val dryrunDpConsumer = factory.createConsumer(config.kafka, Topics.dryrunDp)
    val dryrunTsConsumer = factory.createConsumer(config.kafka, Topics.dryrunTs)
    val dryrunTpConsumer = factory.createConsumer(config.kafka, Topics.dryrunTp)

    val statusJob = launch { statusConsumer(correlator, statusKafkaConsumer) }
    val aapJob = launch { dryrunConsumer(correlator, dryrunAapConsumer) }
    val dpJob = launch { dryrunConsumer(correlator, dryrunDpConsumer) }
    val tsJob = launch { dryrunConsumer(correlator, dryrunTsConsumer) }
    val tpJob = launch { dryrunConsumer(correlator, dryrunTpConsumer) }

    routing {
        probes(prometheus)
        api(correlator)
    }

    monitor.subscribe(ApplicationStopping) {
        producers.close()
        statusJob.cancelJob()
        aapJob.cancelJob()
        dpJob.cancelJob()
        tsJob.cancelJob()
        tpJob.cancelJob()
        kafka.close()
    }
}

fun Job.cancelJob() {
    if (!this.isCompleted) runBlocking(Dispatchers.IO) {
        appLog.info("Job cancelled")
        this@cancelJob.cancelAndJoin()
    }
}


