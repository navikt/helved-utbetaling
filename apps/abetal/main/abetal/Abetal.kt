package abetal

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.KafkaStreams
import libs.kafka.KafkaProducer
import libs.kafka.Streams
import libs.kafka.Topology
import libs.auth.TokenProvider
import libs.auth.jwt
import libs.utils.Log
import models.ApiError
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
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
        module = Application::abetal,
    ).start(wait = true)
}

fun Application.abetal(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    topology: Topology = createTopology(kafka),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }
    install(ContentNegotiation) {
        json(Json { 
            ignoreUnknownKeys = true 
            encodeDefaults = true
        })
    }
    install(Authentication) {
        jwt(TokenProvider.AZURE, config.azure)
    }
    install(StatusPages) {
        exception<ApiError> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.statusCode), cause)
        }
        exception<BadRequestException> { call, cause ->
            val message = "Klarte ikke lese json meldingen. Sjekk at formatet på meldingen din er korrekt, f.eks navn på felter, påkrevde felter, e.l."
            Log.debug(message, cause)
            call.respond(HttpStatusCode.BadRequest, ApiError(statusCode = 400, msg = message))
        }
        exception<Throwable> { call, cause ->
            val message = "Ukjent feil, helved er varslet."
            Log.error(message, cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError(statusCode = 500, msg = message))
        }
    }

    val oppdragProducer: KafkaProducer<String, Oppdrag> =
        kafka.createProducer(config.kafka, Topics.oppdrag)
    val feilregisterAvvent = FeilregisterAvvent(oppdragProducer)
    monitor.subscribe(ApplicationStopping) {
        oppdragProducer.close()
        kafka.close()
    }

    routing {
        probes(kafka, prometheus)
        authenticate(TokenProvider.AZURE) {
            feilregisterAvvent.route(this)
        }
    }

    val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(1))
        .build()

    // Starts Kafka Streams paused, resumes when utsjekk is ready.
    // Pauses again if utsjekk becomes unavailable.
    kafka.start(topology, config.kafka, prometheus) {
        runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI("${config.utsjekk}/actuator/ready"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()
            httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..299
        }.getOrDefault(false)
    }
}
