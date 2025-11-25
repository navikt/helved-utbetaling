package utsjekk.iverksetting

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import libs.utils.appLog
import models.ApiError
import models.DocumentedErrors
import models.badRequest
import models.kontrakter.iverksett.IverksettV2Dto
import models.notFound
import utsjekk.*

fun Route.iverksetting(iverksettingService: IverksettingService) {
    route("/api/iverksetting") {
        post("/v2") {
            val dto = try {
                call.receive<IverksettV2Dto>()
            } catch (ex: Exception) {
                badRequest("Klarte ikke lese request body. Sjekk at du ikke mangler noen felter", "${DocumentedErrors.BASE}/async/kom_i_gang/opprett_utbetaling")
            }

            appLog.info("Behandler sakId ${dto.sakId} behandlingId ${dto.behandlingId}")

            dto.validate()

            val fagsystem = call.fagsystem()
            val iverksetting = Iverksetting.from(dto, fagsystem)

            try {
                iverksettingService.valider(iverksetting)
                iverksettingService.iverksett(iverksetting)
            } catch (e: ApiError) {
                if (e.statusCode != 409) throw e
            }

            call.respond(HttpStatusCode.Accepted)
        }

        get("/{sakId}/{behandlingId}/status") {
            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
            val fagsystem = call.fagsystem()
            val status = iverksettingService.utledStatus(fagsystem, sakId, behandlingId, null)
                ?: notFound("Fant ikke status utbetaling med sakId $sakId og behandlingId $behandlingId")

            call.respond(HttpStatusCode.OK, status)
        }

        get("/{sakId}/{behandlingId}/{iverksettingId}/status") {
            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
            val iverksettingId = call.parameters.getOrFail<String>("iverksettingId").let(::IverksettingId)
            val fagsystem = call.fagsystem()
            val status = iverksettingService.utledStatus(fagsystem, sakId, behandlingId, iverksettingId)
                ?: notFound("Fant ikke status utbetaling med sakId $sakId, behandlingId $behandlingId og iverksettingId $iverksettingId")

            call.respond(HttpStatusCode.OK, status)
        }
    }
}
