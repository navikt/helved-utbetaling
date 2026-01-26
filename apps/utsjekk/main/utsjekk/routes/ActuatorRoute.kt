package utsjekk.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Routing.actuator(meters: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metric") { call.respond(meters.scrape()) }
        get("/health") { call.respond(HttpStatusCode.OK) }
    }
}
