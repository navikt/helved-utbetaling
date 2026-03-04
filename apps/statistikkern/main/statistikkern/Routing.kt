package statistikkern

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Routing.probes(meters: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metric") { call.respond(meters.scrape()) }
        get("/ready") { call.respond(HttpStatusCode.OK) }
        get("/live") { call.respond(HttpStatusCode.OK) }
    }
}