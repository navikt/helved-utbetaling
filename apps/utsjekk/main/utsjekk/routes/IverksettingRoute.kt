package utsjekk.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import no.nav.utsjekk.kontrakter.iverksett.IverksettDto
import utsjekk.ApiError.Companion.forbidden
import utsjekk.iverksetting.*


// todo: denne implementasjonen er ikke riktig, bare en placeholder
private fun ApplicationCall.client(): Client =
    requireNotNull(principal<JWTPrincipal>()) { "principal mangler i ktor auth" }
        .getClaim("azp_name", String::class)
        ?.let(::Client)
        ?: forbidden("mangler azp_name i claims")

fun Route.iverksettingRoute(service: IverksettingService) {
    route("/api/iverksetting") {
        post {
            val dto = call.receive<IverksettDto>()
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
