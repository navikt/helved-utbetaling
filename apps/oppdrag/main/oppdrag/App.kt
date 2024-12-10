package oppdrag

// imports
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
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.ktor.*
import libs.mq.MQ
import libs.postgres.Jdbc
import libs.postgres.JdbcConfig
import libs.postgres.Migrator
import libs.utils.logger
import libs.utils.secureLog
import oppdrag.grensesnittavstemming.AvstemmingMQProducer
import oppdrag.grensesnittavstemming.GrensesnittavstemmingService
import oppdrag.iverksetting.OppdragMQConsumer
import oppdrag.iverksetting.OppdragService
import oppdrag.routing.actuators
import oppdrag.routing.avstemmingRoutes
import oppdrag.routing.iverksettingRoutes

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080) {
        val config = Config()
        database(config.postgres)
        server(config)
    }.start(wait = true)
}

fun Application.database(config: JdbcConfig) {
    Jdbc.initialize(config)

    runBlocking {
        withContext(Jdbc.context) {
            Migrator(File("migrations")).migrate()
        }
    }
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

    install(DoubleReceive)
    install(CallLog) {
        exclude { call -> call.request.path().startsWith("/probes") }
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


    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    val mq = MQ(config.mq)
    val oppdragConsumer = OppdragMQConsumer(config.oppdrag, mq)
    val oppdragService = OppdragService(config.oppdrag, mq)
    val avstemmingProducer = AvstemmingMQProducer(mq, config.avstemming)
    val avstemmingService = GrensesnittavstemmingService(avstemmingProducer)

    monitor.subscribe(ApplicationStopping) {
        oppdragConsumer.close()
    }

    monitor.subscribe(ApplicationStarted) {
        oppdragConsumer.start()
    }

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksettingRoutes(oppdragService)
            avstemmingRoutes(avstemmingService)
        }
        actuators(prometheus)
    }
}
