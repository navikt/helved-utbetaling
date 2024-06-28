package simulering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import libs.utils.appLog
import libs.utils.secureLog
import org.slf4j.event.Level
import simulering.routing.actuators
import simulering.routing.simulering

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> appLog.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::app).start(wait = true)
}

fun Application.app(config: Config = Config()) {

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

    install(DoubleReceive) // TODO: Used to log request body in CallLogging.

    install(CallLogging) {
        level = Level.INFO
        format { call ->
            runBlocking {
                buildString {
                    appendLine("${call.request.httpMethod.value} ${call.request.local.uri} ${call.response.status()}")
                    appendLine("${call.request.headers}")
                    appendLine(call.receiveText()) // requires install(DoubleReceive)
                }
            }
        }
        filter { call -> call.request.path().startsWith("/actuator").not() }

        logger = secureLog
    }

    val simulering = SimuleringService(config)

    routing {
        actuators(prometheus)
        simulering(simulering, prometheus)
    }
}
