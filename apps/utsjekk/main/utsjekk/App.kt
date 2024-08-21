package utsjekk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.getunleash.Unleash
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import libs.auth.TokenProvider
import libs.auth.configure
import libs.postgres.Postgres
import libs.postgres.Postgres.migrate
import libs.postgres.concurrency.CoroutineDatasource
import libs.utils.appLog
import libs.utils.secureLog
import utsjekk.featuretoggle.FeatureToggles
import utsjekk.iverksetting.IverksettingService
import utsjekk.oppdrag.OppdragClient
import utsjekk.routes.actuators
import utsjekk.routes.iverksettingRoute
import utsjekk.routes.tasks
import utsjekk.task.TaskScheduler
import kotlin.coroutines.CoroutineContext

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val config = Config()
    val datasource = Postgres.initialize(config.postgres).apply { migrate() }
    val context = Dispatchers.IO + CoroutineDatasource(datasource)

    embeddedServer(Netty, port = 8080) {
        utsjekk(config, context)
    }.start(wait = true)
}

fun Application.utsjekk(
    config: Config,
    context: CoroutineContext,
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
                is ApiError.BadRequest -> call.respond(HttpStatusCode.BadRequest, cause.message)
                is ApiError.NotFound -> call.respond(HttpStatusCode.NotFound, cause.message)
                is ApiError.Conflict -> call.respond(HttpStatusCode.Conflict, cause.message)
                is ApiError.Forbidden -> call.respond(HttpStatusCode.Forbidden, cause.message)
                is ApiError.Unavailable -> call.respond(HttpStatusCode.ServiceUnavailable, cause.message)
                else -> {
                    secureLog.error("Unknown error.", cause)
                    call.respond(HttpStatusCode.UnprocessableEntity, "Unknown error. See logs")
                }
            }
        }
    }

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    val oppdrag = OppdragClient(config)
    val scheduler = TaskScheduler(oppdrag, context)
    val toggles = FeatureToggles(config.unleash)
    val iverksettingService = IverksettingService(toggles)

    environment.monitor.subscribe(ApplicationStopping) {
        scheduler.close()
    }

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksettingRoute(iverksettingService)
            tasks(context)
        }

        actuators(prometheus)
    }
}
