package abetal

import abetal.models.AapUtbetaling
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.StateStore
import libs.kafka.Streams
import models.Utbetaling

fun Routing.probes(kafka: Streams, meters: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metric") { 
            call.respond(meters.scrape())
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

fun Routing.api(stateStore: StateStore<String, Utbetaling>) {
    route("/api") {

        get("/utbetalinger/{uid}"){
            when(val uid = call.parameters["uid"]) {
                null -> call.respond(HttpStatusCode.BadRequest, "path param uid missing")
                else -> when(val utbetaling = stateStore.getOrNull(uid)){
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(utbetaling)
                }
            }
        }
    }
}

