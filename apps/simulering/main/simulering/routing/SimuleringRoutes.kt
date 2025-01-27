package simulering.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.micrometer.core.instrument.MeterRegistry
import simulering.FinnesFraFør
import simulering.IkkeFunnet
import simulering.OppdragErStengtException
import simulering.RequestErUgyldigException
import simulering.SimuleringService
import simulering.models.rest.UtbetalingsoppdragDto
import simulering.models.rest.rest
import simulering.models.soap.soap.SimulerBeregningRequest

fun Routing.simulering(
    service: SimuleringService,
    metrics: MeterRegistry,
) {
    post("/simuler") {
        runCatching {
            val dto: UtbetalingsoppdragDto = call.receive()
            val request = SimulerBeregningRequest.from(dto)
            service.simuler(request)
        }.onSuccess { sim ->
            call.respond(HttpStatusCode.OK, sim)
        }.onFailure { ex ->
            when (ex) {
                is IkkeFunnet -> call.respond(HttpStatusCode.NotFound, ex.message!!)
                is FinnesFraFør -> call.respond(HttpStatusCode.Conflict, ex.message!!)
                is RequestErUgyldigException -> call.respond(HttpStatusCode.BadRequest, ex.message!!)
                is OppdragErStengtException -> call.respond(HttpStatusCode.ServiceUnavailable, ex.message!!)
                else -> call.respond(HttpStatusCode.InternalServerError, ex.message!!).also {
                    metrics.counter("soap_error_unknown").increment()
                }
            }
        }
    }

    route("/simulering") {
        post {
            runCatching {
                val request: rest.SimuleringRequest = call.receive()
                service.simuler(request)
            }.onSuccess { sim ->
                call.respond(sim)
            }.onFailure { ex ->
                when (ex) {
                    is IkkeFunnet -> call.respond(HttpStatusCode.NotFound, ex.message!!)
                    is FinnesFraFør -> call.respond(HttpStatusCode.Conflict, ex.message!!)
                    is RequestErUgyldigException -> call.respond(HttpStatusCode.BadRequest, ex.message!!)
                    is OppdragErStengtException -> call.respond(HttpStatusCode.ServiceUnavailable, ex.message!!)
                    else -> call.respond(HttpStatusCode.InternalServerError, ex.message!!).also {
                        metrics.counter("soap_error_unknown").increment()
                    }
                }
            }
        }
    }
}
