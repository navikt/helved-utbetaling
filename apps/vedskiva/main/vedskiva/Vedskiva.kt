package vedskiva

import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import libs.auth.JwksClient
import libs.auth.JwtVerifier
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.context
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.utils.Log
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.lens.LensFailure
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.net.http.HttpClient
import java.time.Duration

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val app = vedskiva()
    app.asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50)))).start().block()
}

fun vedskiva(
    config: Config = Config(),
    streams: Streams = KafkaStreams()
): HttpHandler {
    val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    LogbackMetrics().bindTo(metrics)
    val jdbcCtx = Jdbc.initialize(config.jdbc).context()
    runBlocking(jdbcCtx) {
        Migrator(config.jdbc.migrations).migrate()
    }

    val producer = streams.createProducer(config.kafka, Topics.avstemming)
    val service = AvstemmingService(producer)
    streams.connect(
        config = config.kafka,
        registry = metrics,
        topology = topology(jdbcCtx)
    )

    val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
    val verifier = JwtVerifier(JwksClient(config.azure.jwks, http), config.azure)
    val auth = ServerFilters.BearerAuth { token ->
        try {
            verifier.verify(token)
            true
        } catch (e: Exception) {
            Log.warn("Token validation failed", e)
            false
        }
    }

    return errorFilter
        .then(ServerFilters.MicrometerMetrics.RequestTimer(metrics))
        .then(ServerFilters.MicrometerMetrics.RequestCounter(metrics))
        .then(routes(
            actuatorRoutes(metrics),
            "/api" bind auth.then(avstem(service, jdbcCtx))
        ))
}

private val errorFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: LensFailure) {
            Log.warn("Invalid request body", e)
            Response(Status.BAD_REQUEST).body("Invalid request body")
        } catch (e: Exception) {
            Log.error("Uhåndtert feil i ${request.method} ${request.uri.path}", e)
            Response(Status.INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }
}
