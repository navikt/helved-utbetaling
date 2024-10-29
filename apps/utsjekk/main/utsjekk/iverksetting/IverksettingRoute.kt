package utsjekk.iverksetting

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import no.nav.utsjekk.kontrakter.iverksett.IverksettV2Dto
import utsjekk.ApiError.Companion.badRequest
import utsjekk.ApiError.Companion.notFound
import utsjekk.appLog
import utsjekk.client

fun Route.iverksetting(service: Iverksettinger) {
    route("/api/iverksetting") {
        post("/v2") {
            val dto = try {
                call.receive<IverksettV2Dto>()
            } catch (ex: Exception) {
                badRequest("Klarte ikke lese request body. Sjekk at du ikke mangler noen felter")
            }

            appLog.info("Behandler sakId ${dto.sakId} behandlingId ${dto.behandlingId}")

            dto.validate()

            val fagsystem = call.client().toFagsystem()
            val iverksetting = Iverksetting.from(dto, fagsystem)

            service.valider(iverksetting)
            service.iverksett(iverksetting)

            call.respond(HttpStatusCode.Accepted)
        }

        post("/tilleggstonader") {
            call.respond(HttpStatusCode.Accepted)
        }

        get("/{sakId}/{behandlingId}/status") {
            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
            val fagsystem = call.client().toFagsystem()
            val status = service.utledStatus(fagsystem, sakId, behandlingId, null)
                ?: notFound("status for sakId $sakId og behandlingId $behandlingId")

            call.respond(HttpStatusCode.OK, status)
        }

        get("/{sakId}/{behandlingId}/{iverksettingId}/status") {
            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
            val iverksettingId = call.parameters.getOrFail<String>("iverksettingId").let(::IverksettingId)
            val fagsystem = call.client().toFagsystem()
            val status = service.utledStatus(fagsystem, sakId, behandlingId, iverksettingId)
                ?: notFound("status for sakId $sakId, behandlingId $behandlingId og iverksettingId $iverksettingId")

            call.respond(HttpStatusCode.OK, status)
        }
    }
}
