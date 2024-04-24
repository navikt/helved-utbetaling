package oppdrag

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import libs.auth.TokenProvider
import libs.auth.configure
import libs.mq.MQ
import libs.utils.appLog
import libs.utils.secureLog
import oppdrag.grensesnittavstemming.AvstemmingMQProducer
import oppdrag.grensesnittavstemming.GrensesnittavstemmingService
import oppdrag.iverksetting.OppdragService
import oppdrag.iverksetting.OppdragMQConsumer
import oppdrag.postgres.Postgres
import oppdrag.routing.actuators
import oppdrag.routing.avstemmingRoutes
import oppdrag.routing.iverksettingRoutes

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }
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
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    val postgres = Postgres.createAndMigrate(config.postgres)
    val mq = MQ(config.mq)

    val oppdragConsumer = OppdragMQConsumer(config.oppdrag, mq, postgres)
    val oppdragService = OppdragService(config.oppdrag, mq, postgres)

    val avstemmingProducer = AvstemmingMQProducer(mq, config.avstemming)
    val avstemmingService = GrensesnittavstemmingService(avstemmingProducer, postgres)

    environment.monitor.subscribe(ApplicationStopping) {
        oppdragConsumer.close()
    }

    environment.monitor.subscribe(ApplicationStarted) {
        oppdragConsumer.start()
    }

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksettingRoutes(oppdragService, postgres)
            avstemmingRoutes(avstemmingService)
        }
//        openAPI(path = "api", swaggerFile = "openapi.yml")
        actuators(prometheus)
    }
}
