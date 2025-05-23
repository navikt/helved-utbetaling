package urskog

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.*
import libs.kafka.Streams

fun Routing.probes(kafka: Streams, micrometer: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metric") {
            call.respond(micrometer.scrape())
        }
        get("/ready") { 
            when (kafka.ready()) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.Locked)
            }
        }
        get("/live") {
            when (kafka.live()) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.Locked)
            }
        }
    }
}
