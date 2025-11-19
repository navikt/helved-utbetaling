package simulering.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.micrometer.core.instrument.MeterRegistry
import simulering.SimuleringService
import simulering.models.rest.UtbetalingsoppdragDto
import simulering.models.rest.rest
import simulering.models.soap.soap.SimulerBeregningRequest

fun Routing.simulering(service: SimuleringService) {
    route("/simuler") {
        post {
            val dto: UtbetalingsoppdragDto = call.receive()
            val request = SimulerBeregningRequest.from(dto)
            val sim = service.simuler(request)
            call.respond(HttpStatusCode.OK, sim)
        }
    }

    route("/simulering") {
        post {
            val request: rest.SimuleringRequest = call.receive()
            val sim = service.simuler(request)
            call.respond(sim)
        }
    }
}
