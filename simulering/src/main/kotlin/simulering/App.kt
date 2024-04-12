package simulering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import felles.appLog
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import simulering.routing.actuators
import simulering.routing.simulering
import simulering.ws.Soap
import simulering.ws.SoapClient
import simulering.ws.Sts
import simulering.ws.StsClient

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> appLog.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::app).start(wait = true)
}

fun Application.app(
    config: Config = Config(),
    sts: Sts = StsClient(config.simulering.sts),
    soap: Soap = SoapClient(config.simulering, sts),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            JavaTimeModule()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    val simulering = SimuleringService(soap)

    routing {
        actuators(prometheus)
        simulering(simulering)
    }
}
