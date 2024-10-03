package utsjekk.simulering

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utsjekk.ApiError.Companion.unauthorized
import utsjekk.client
import utsjekk.clients.SimuleringClient

fun Route.simulering(validator: SimuleringValidator, client: SimuleringClient) {

    route("/api/simulering/v2") {
        post {
            val fagsystem = call.client().toFagsystem()
            val dto = call.receive<api.SimuleringRequest>()
            val simulering = Simulering.from(dto, fagsystem)
            validator.valider(simulering)
            val token = call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing")
            when (val res = client.hentSimuleringsresultatMedOppsummering(simulering, token)) {
                null -> call.respond(HttpStatusCode.NoContent)
                else -> call.respond(res)
            }
        }
    }
}
