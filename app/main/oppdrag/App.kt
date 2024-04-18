package oppdrag

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
import io.ktor.server.plugins.openapi.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import libs.auth.TokenProvider
import libs.auth.configure
import libs.mq.MQFactory
import libs.utils.appLog
import oppdrag.grensesnittavstemming.GrensesnittavstemmingProducer
import oppdrag.grensesnittavstemming.GrensesnittavstemmingService
import oppdrag.grensesnittavstemming.grensesnittavstemmingRoute
import oppdrag.iverksetting.OppdragService
import oppdrag.iverksetting.iverksettingRoute
import oppdrag.iverksetting.mq.OppdragMQConsumer
import oppdrag.postgres.Postgres

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> appLog.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(config: Config = Config()) {
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

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    val datasource = Postgres.createAndMigrate(config.postgres)
    val mqFactory = MQFactory.new(config.mq)

    val oppdragConsumer = OppdragMQConsumer(
        config = config,
        postgres = datasource,
    )

    val oppdragService = OppdragService(
        config = config,
        postgres = datasource,
        factory = mqFactory,
    )

    val avstemmingService = GrensesnittavstemmingService(
        producer = GrensesnittavstemmingProducer(config, mqFactory),
        postgres = datasource,
    )

    environment.monitor.subscribe(ApplicationStopping) {
        oppdragConsumer.close()
    }

    environment.monitor.subscribe(ApplicationStarted) {
        oppdragConsumer.start()
    }

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksettingRoute(oppdragService, datasource)
            grensesnittavstemmingRoute(avstemmingService)
        }
        openAPI(path = "api", swaggerFile = "openapi.yml")
        actuators(prometheus)
    }
}


private fun Routing.actuators(prometheus: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metrics") {
            call.respond(prometheus.scrape())
        }
        get("/live") {
            call.respond(HttpStatusCode.OK, "live")
        }
        get("/ready") {
            call.respond(HttpStatusCode.OK, "ready")
        }
    }
}
