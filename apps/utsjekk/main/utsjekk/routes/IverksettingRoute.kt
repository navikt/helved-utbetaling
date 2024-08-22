package utsjekk.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import no.nav.utsjekk.kontrakter.iverksett.IverksettV2Dto
import utsjekk.ApiError.Companion.badRequest
import utsjekk.ApiError.Companion.forbidden
import utsjekk.iverksetting.BehandlingId
import utsjekk.iverksetting.Client
import utsjekk.iverksetting.Iverksetting
import utsjekk.iverksetting.IverksettingService
import utsjekk.iverksetting.SakId
import utsjekk.iverksetting.from


// todo: denne implementasjonen er ikke riktig, bare en placeholder
private fun ApplicationCall.client(): Client =
    requireNotNull(principal<JWTPrincipal>()) { "principal mangler i ktor auth" }
        .getClaim("azp_name", String::class)
        ?.let(::Client)
        ?: forbidden("mangler azp_name i claims")

fun Route.iverksettingRoute(service: IverksettingService) {
    route("/api/iverksetting") {
        post("/v2") {
            val dto = try {
                call.receive<IverksettV2Dto>()
            } catch (ex: Exception) {
                badRequest("Klarte ikke lese request body. Sjekk at du ikke mangler noen felter")
            }
            val iverksetting = Iverksetting.from(dto)

            service.iverksett(iverksetting)

            call.respond(HttpStatusCode.Accepted)
        }

        post("/tilleggstonader") {
            call.respond(HttpStatusCode.Accepted)
        }

        get("/{sakId}/{behandlingId}/status") {
            val sakId = call.parameters.getOrFail<SakId>("sakId")
            val behandlingId = call.parameters.getOrFail<BehandlingId>("behandlingId")
            val client = call.client()
            val status = service.utledStatus(client, sakId, behandlingId)

            call.respond(HttpStatusCode.OK, status)
        }

        get("/{sakId}/{behandlingId}/{iverksettingId}/status") {

        }
    }
}
