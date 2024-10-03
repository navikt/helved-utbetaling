package oppdrag.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.withContext
import libs.postgres.Postgres
import libs.postgres.concurrency.transaction
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import oppdrag.iverksetting.OppdragAlleredeSendtException
import oppdrag.iverksetting.OppdragService
import oppdrag.iverksetting.domene.OppdragMapper
import oppdrag.iverksetting.tilstand.OppdragId

fun Route.iverksettingRoutes(
    oppdragService: OppdragService,
) {
    post("/oppdrag") {
        withContext(Postgres.context) {
            val utbetalingsoppdrag = call.receive<Utbetalingsoppdrag>()

            runCatching {
                opprettOppdrag(oppdragService, utbetalingsoppdrag, 0)
            }.onSuccess {
                call.respond(HttpStatusCode.Created)
            }.onFailure {
                when (it) {
                    is OppdragAlleredeSendtException -> oppdragAlleredeSendt(utbetalingsoppdrag)
                    else -> {
                        secureLog.error("Klarte ikke sende oppdrag for saksnr ${utbetalingsoppdrag.saksnummer}", it)
                        klarteIkkeSendeOppdrag(utbetalingsoppdrag)
                    }
                }
            }
        }
    }

    post("/oppdragPaaNytt/{versjon}") {
        withContext(Postgres.context) {
            val utbetalingsoppdrag = call.receive<Utbetalingsoppdrag>()
            val versjon = call.parameters["versjon"]?.toInt() ?: 0

            runCatching {
                opprettOppdrag(oppdragService, utbetalingsoppdrag, versjon)
            }.onSuccess {
                call.respond(HttpStatusCode.Created)
            }.onFailure {
                klarteIkkeSendeOppdrag(utbetalingsoppdrag)
            }
        }
    }

    post("/status") {
        withContext(Postgres.context) {
            val dto = call.receive<OppdragIdDto>()

            runCatching {
                val oppdragId = OppdragId(
                    fagsystem = dto.fagsystem,
                    fagsakId = dto.sakId,
                    behandlingId = dto.behandlingId,
                    iverksettingId = dto.iverksettingId,
                )

                withContext(Postgres.context) {
                    transaction {
                        oppdragService.hentStatusForOppdrag(oppdragId)
                    }
                }

            }.onSuccess {
                call.respond(
                    HttpStatusCode.OK,
                    OppdragStatusDto(
                        status = it.status,
                        feilmelding = it.kvitteringsmelding?.beskrMelding,
                    ),
                )
            }.onFailure {
                call.respond(HttpStatusCode.NotFound, "Fant ikke oppdrag med id $dto")
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.klarteIkkeSendeOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag) {
    appLog.error("Klarte ikke sende oppdrag for saksnr ${utbetalingsoppdrag.saksnummer}")
    call.respond(
        HttpStatusCode.InternalServerError,
        "Klarte ikke sende oppdrag for saksnr ${utbetalingsoppdrag.saksnummer}",
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.oppdragAlleredeSendt(utbetalingsoppdrag: Utbetalingsoppdrag) {
    appLog.info("Oppdrag er allerede sendt for saksnr ${utbetalingsoppdrag.saksnummer}")
    call.respond(
        HttpStatusCode.Conflict,
        "Oppdrag er allerede sendt for saksnr ${utbetalingsoppdrag.saksnummer}",
    )
}

private suspend fun opprettOppdrag(
    oppdragService: OppdragService,
    utbetalingsoppdrag: Utbetalingsoppdrag,
    versjon: Int,
) {
    val oppdrag110 = OppdragMapper.tilOppdrag110(utbetalingsoppdrag)
    val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)

    oppdragService.opprettOppdrag(utbetalingsoppdrag, oppdrag, versjon)
}
