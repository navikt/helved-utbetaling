package peisschtappern

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.kafka.KafkaFactory
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.postgres.Jdbc
import libs.postgres.Migrator
import libs.utils.*

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
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
        module = Application::peisschtappern,
    ).start(wait = true)
}

fun Application.peisschtappern(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    kafkaFactory: KafkaFactory = DefaultKafkaFactory()
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    kafka.connect(
        topology = createTopology(),
        config = config.kafka,
        registry = prometheus
    )

    val oppdragsdataProducer = kafkaFactory.createProducer(config.kafka, oppdrag)
    val manuellKvitteringService = ManuellKvitteringService(oppdragsdataProducer)

    routing {
        probes(kafka, prometheus)

        authenticate(TokenProvider.AZURE) {
            api(manuellKvitteringService)
        }
    }

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
        oppdragsdataProducer.close()
    }
}
