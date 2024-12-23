package simulering

// imports
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import libs.ktor.*
import libs.utils.logger
import libs.utils.secureLog
import org.slf4j.event.Level
import simulering.routing.actuators
import simulering.routing.simulering

val appLog = logger("app")

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

    install(DoubleReceive)
    install(CallLog) {
        exclude { call -> call.request.path().startsWith("/actuator") }
        log { call ->
            appLog.info("${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms")
            secureLog.info(
                """
                ${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms
                ${call.bodyAsText()}
                """.trimIndent()
            )
        }
    }

    val simulering = SimuleringService(config)

    routing {
        actuators(prometheus)
        simulering(simulering, prometheus)
    }
}
