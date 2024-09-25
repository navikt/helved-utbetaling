package utsjekk

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Routing.probes(meters: PrometheusMeterRegistry) {
    route("/probes") {
        get("/metric") { call.respond(meters.scrape()) }
        get("/health") { call.respond(HttpStatusCode.OK) }
        get("/live") { call.respond(HttpStatusCode.OK, "live") }
        get("/ready") { call.respond(HttpStatusCode.OK, "ready") }
    }
}
