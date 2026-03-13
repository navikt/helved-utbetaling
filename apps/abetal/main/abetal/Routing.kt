package abetal

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Streams
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

fun Routing.probes(
    kafka: Streams,
    meters: PrometheusMeterRegistry,
    utsjekk: URL,
    kafkaStarted: AtomicBoolean,
) {
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 2000
            connectTimeoutMillis = 1000
        }
    }

    route("/actuator") {
        get("/metric") {
            call.respond(meters.scrape())
        }
        get("/ready") {
            val kafkaReady = kafkaStarted.get() && kafka.ready()
            val utsjekReady = runCatching {
                client.get("$utsjekk/actuator/health").status.value in 200..299
            }.getOrDefault(false)

            when (kafkaReady && utsjekReady) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.Locked)
            }
        }
        get("/live") {
            when {
                !kafkaStarted.get() -> call.respond(HttpStatusCode.OK)
                kafka.live() -> call.respond(HttpStatusCode.OK)
                else -> call.respond(HttpStatusCode.Locked)
            }
        }
    }
}
