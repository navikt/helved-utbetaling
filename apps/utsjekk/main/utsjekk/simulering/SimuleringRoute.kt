package utsjekk.simulering

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.authorization
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import models.unauthorized
import utsjekk.*
import utsjekk.clients.SimuleringClient

fun Route.simulering(validator: SimuleringValidator, client: SimuleringClient) {

    route("/api/simulering/v2") {
        post {
            val fagsystem = call.fagsystem()
            val dto = call.receive<api.SimuleringRequest>()
            val simulering = Simulering.from(dto, fagsystem)
            validator.valider(simulering)

            val token = if (call.hasClaim("NAVident")) {
                TokenType.Obo(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
            } else if (call.hasClaim("azp_name")) {
                TokenType.Client(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
            } else {
                unauthorized("Mangler claims")
            }

            when (val res = client.hentSimuleringsresultatMedOppsummering(simulering, token)) {
                null -> call.respond(HttpStatusCode.NoContent)
                else -> call.respond(res)
            }
        }
    }
}
