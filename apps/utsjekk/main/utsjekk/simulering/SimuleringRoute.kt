package utsjekk.simulering

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utsjekk.client
import utsjekk.hasClaim
import utsjekk.clients.SimuleringClient
import utsjekk.unauthorized
import utsjekk.TokenType

fun Route.simulering(validator: SimuleringValidator, client: SimuleringClient) {

    route("/api/simulering/v2") {
        post {
            val fagsystem = call.client().toFagsystem()
            val dto = call.receive<api.SimuleringRequest>()
            val simulering = Simulering.from(dto, fagsystem)
            validator.valider(simulering)

            val token = if (call.hasClaim("NAVident")) {
                TokenType.Obo(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing"))
            } else if (call.hasClaim("azp_name")) {
                TokenType.Client(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing"))
            } else {
                unauthorized("missing required claim", "azp_name or NAVident", "kom_i_gang")
            }

            when (val res = client.hentSimuleringsresultatMedOppsummering(simulering, token)) {
                null -> call.respond(HttpStatusCode.NoContent)
                else -> call.respond(res)
            }
        }
    }
}
