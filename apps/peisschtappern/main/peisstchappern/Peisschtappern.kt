package peisstchappern

import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.postgres.Jdbc
import libs.postgres.Migrator
import libs.utils.*
import java.io.File

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::peisschtappern).start(wait = true)
}

fun Application.peisschtappern(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    kafka.connect(
        topology = createTopology(),
        config = config.kafka, 
        registry = prometheus
    )

    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    routing {
        probes(kafka, prometheus)

        authenticate(TokenProvider.AZURE) {

        }
    }

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
    }
}
