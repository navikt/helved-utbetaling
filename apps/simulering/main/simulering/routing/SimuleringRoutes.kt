package simulering.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import simulering.OppdragErStengtException
import simulering.PersonFinnesIkkeException
import simulering.RequestErUgyldigException
import simulering.SimuleringService
import simulering.dto.SimuleringApiDto

fun Routing.simulering(
    simulering: SimuleringService,
) {
    route("/simulering") {
        post {
            runCatching {
                val request: SimuleringApiDto = call.receive()
                simulering.simuler(request)
            }.onSuccess { sim ->
                call.respond(sim)
            }.onFailure { ex ->
                when (ex) {
                    is PersonFinnesIkkeException -> call.respond(HttpStatusCode.NotFound, ex.message!!)
                    is RequestErUgyldigException -> call.respond(HttpStatusCode.BadRequest, ex.message!!)
                    is OppdragErStengtException -> call.respond(HttpStatusCode.ServiceUnavailable, ex.message!!)
                    else -> call.respond(HttpStatusCode.InternalServerError, ex.message!!)
                }
            }
        }
    }
}
