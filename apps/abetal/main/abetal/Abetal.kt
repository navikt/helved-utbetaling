package abetal

import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.KafkaStreams
import libs.kafka.KafkaProducer
import libs.kafka.Streams
import libs.kafka.Topology
import libs.utils.Log
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import libs.auth.JwksClient
import libs.auth.JwtVerifier
import org.http4k.core.HttpHandler
import org.http4k.core.then
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.routing.routes
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val app = abetal()
    val server = app.handler
        .asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50))))
        .start()

    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            app.close()
        } finally {
            server.stop()
        }
    })

    server.block()
}

class Abetal(val handler: HttpHandler, private val closeables: List<AutoCloseable>) : AutoCloseable {
    override fun close() = closeables.forEach { it.close() }
}

fun abetal(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    topology: Topology = createTopology(kafka)
): Abetal {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val logbackMetrics = LogbackMetrics().also { it.bindTo(prometheus) }

    val oppdragProducer: KafkaProducer<String, Oppdrag> =
        kafka.createProducer(config.kafka, Topics.oppdrag)
    val feilregisterAvvent = FeilregisterAvvent(oppdragProducer)
    val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(1))
        .build()

    val verifier = JwtVerifier(JwksClient(config.azure.jwks, httpClient), config.azure)
    val handler = ServerFilters.MicrometerMetrics.RequestTimer(prometheus)
        .then(ServerFilters.MicrometerMetrics.RequestCounter(prometheus))
        .then(errorFilter)
        .then(routes(
            probes(kafka, prometheus),
            azureAuthFilter(verifier).then(feilregisterAvvent.route())
        ))

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

    return Abetal(
        handler,
        listOf(kafka, oppdragProducer, httpClient, logbackMetrics, AutoCloseable { prometheus.close() })
    )
}
