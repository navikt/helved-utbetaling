package utsjekk.iverksetting

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import libs.utils.appLog
import models.kontrakter.iverksett.IverksettV2Dto
import utsjekk.*

fun Route.iverksetting(iverksettingService: IverksettingService) {
    route("/api/iverksetting") {
        post("/v2") {
            val dto = try {
                call.receive<IverksettV2Dto>()
            } catch (ex: Exception) {
                badRequest("Klarte ikke lese request body. Sjekk at du ikke mangler noen felter")
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
                ?: notFound("status for sakId $sakId og behandlingId $behandlingId")

            call.respond(HttpStatusCode.OK, status)
        }

//        put("/{sakId}/{behandlingId}/{fagsystem}/kvitter_ok") {
//            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
//            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
//            val fagsystem = call.parameters.getOrFail<String>("fagsystem").let { Fagsystem.valueOf(it) }
//            withContext(Jdbc.context) {
//                transaction {
//                    val resultat = IverksettingResultatDao.select(1) {
//                        this.iverksettingId = null
//                        this.behandlingId = behandlingId
//                        this.sakId = sakId
//                        this.fagsystem = fagsystem
//                    }.singleOrNull()
//
//                    if (resultat == null) {
//                        IverksettingResultatDao(
//                            iverksettingId = null,
//                            behandlingId = behandlingId,
//                            sakId = sakId,
//                            fagsystem = fagsystem,
//                            oppdragResultat = OppdragResultat(OppdragStatus.KVITTERT_OK)
//                        ).insert()
//                    }
//                }
//            }
//
//            call.respond(HttpStatusCode.OK, )
//        }

        get("/{sakId}/{behandlingId}/{iverksettingId}/status") {
            val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
            val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
            val iverksettingId = call.parameters.getOrFail<String>("iverksettingId").let(::IverksettingId)
            val fagsystem = call.fagsystem()
            val status = iverksettingService.utledStatus(fagsystem, sakId, behandlingId, iverksettingId)
                ?: notFound("status for sakId $sakId, behandlingId $behandlingId og iverksettingId $iverksettingId")

            call.respond(HttpStatusCode.OK, status)
        }
    }
}
