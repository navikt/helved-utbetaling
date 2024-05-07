package utsjekk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import libs.auth.TokenProvider
import libs.auth.configure
import libs.postgres.Postgres
import libs.postgres.Postgres.migrate
import libs.task.TaskService
import libs.utils.appLog
import libs.utils.secureLog
import utsjekk.routing.actuators
import utsjekk.routing.task
import utsjekk.task.TaskScheduler

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }
    embeddedServer(Netty, port = 8080, module = Application::utsjekk).start(wait = true)
}

fun Application.utsjekk(config: Config = Config()) {
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
                is BadRequest -> call.respond(cause.code, cause.message)
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

    val postgres = Postgres.initialize(config.postgres).apply {
        migrate()
    }
    val scheduler = TaskScheduler()

    environment.monitor.subscribe(ApplicationStopping) {
        scheduler.close()
    }

    routing {
        authenticate(TokenProvider.AZURE) {
            task()
        }

        actuators(prometheus)
    }
}
