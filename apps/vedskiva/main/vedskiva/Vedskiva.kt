package vedskiva

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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.kafka.KafkaFactory
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.utils.appLog
import libs.utils.secureLog

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
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
        module = Application::vedskiva,
    ).start(wait = true)
}

fun Application.vedskiva(
    config: Config = Config(),
    streams: Streams = KafkaStreams(),
    kafka: KafkaFactory = Kafka(), // FIXME: streams har en innebygget ekvivalent
) {
    val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = metrics
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

    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    val producer = kafka.createProducer(config.kafka, Topics.avstemming) 
    val service = AvstemmingService(config, producer)

    streams.connect(
        config = config.kafka,
        registry = metrics,
        topology = topology()
    )
    routing {
        authenticate(TokenProvider.AZURE) {
            avstem(service)
        }
        route("/actuator") {
            get("/metric") { call.respond(metrics.scrape()) }
            get("/health") { call.respond(HttpStatusCode.OK) }
        }
    }
}

