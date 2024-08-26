package utsjekk.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import no.nav.utsjekk.kontrakter.iverksett.IverksettV2Dto
import utsjekk.ApiError.Companion.badRequest
import utsjekk.ApiError.Companion.forbidden
import utsjekk.ApiError.Companion.notFound
import utsjekk.iverksetting.*


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
            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
            val client = call.client()
            val status = service.utledStatus(client, sakId, behandlingId, null)
                ?: notFound("status for sakId $sakId og behandlingId $behandlingId")

            call.respond(HttpStatusCode.OK, status)
        }

        get("/{sakId}/{behandlingId}/{iverksettingId}/status") {
            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
            val iverksettingId = call.parameters.getOrFail<String>("iverksettingId").let(::IverksettingId)
            val client = call.client() // fixme: should be possible to set azp_name claim in newest helved:libs:auth-test
            val status = service.utledStatus(client, sakId, behandlingId, iverksettingId)
                ?: notFound("status for sakId $sakId, behandlingId $behandlingId og iverksettingId $iverksettingId")

            call.respond(HttpStatusCode.OK, status)
        }
    }
}
