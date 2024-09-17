package utsjekk.simulering

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utsjekk.ApiError
import utsjekk.ApiError.Companion.unauthorized
import utsjekk.iverksetting.Client

private fun ApplicationCall.client(): Client =
    requireNotNull(principal<JWTPrincipal>()) { "principal mangler i ktor auth" }
        .getClaim("azp_name", String::class)?.split(":")?.last()
        ?.let(::Client)
        ?: ApiError.forbidden("mangler azp_name i claims")

fun Route.simulering(validator: SimuleringValidator, client: SimuleringClient) {

    route("/api/simulering/v2") {
        post {
            val fagsystem = call.client().toFagsystem()
            val dto = call.receive<api.SimuleringRequest>()
            val simulering = Simulering.from(dto, fagsystem)
            validator.valider(simulering)
            val token = call.request.authorization() ?: unauthorized("auth header missing")
            when (val res = client.hentSimuleringsresultatMedOppsummering(simulering, token)) {
                null -> call.respond(HttpStatusCode.NoContent)
                else -> call.respond(res)
            }
        }
    }
}