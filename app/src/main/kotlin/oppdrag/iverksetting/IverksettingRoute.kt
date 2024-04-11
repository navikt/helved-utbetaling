package oppdrag.iverksetting

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import oppdrag.iverksetting.domene.OppdragMapper
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.postgres.transaction
import javax.sql.DataSource

// todo: azuread auth
fun Routing.iverksettingRoute(
    oppdragService: OppdragService,
    postgres: DataSource,
) {
    post("/oppdrag") {
        val utbetalingsoppdrag = call.receive<Utbetalingsoppdrag>()

        runCatching {
            opprettOppdrag(oppdragService, utbetalingsoppdrag, 0)
        }.onSuccess {
            call.respond(HttpStatusCode.Created)
        }.onFailure {
            when (it) {
                is OppdragAlleredeSendtException -> oppdragAlleredeSendt(utbetalingsoppdrag)
                else -> klarteIkkeSendeOppdrag(utbetalingsoppdrag)
            }
        }
    }

    post("/oppdragPaaNytt/{versjon}") {
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

    post("/status") {
        val dto = call.receive<OppdragIdDto>()

        runCatching {
            val oppdragId = OppdragId(
                fagsystem = dto.fagsystem,
                fagsakId = dto.sakId,
                behandlingId = dto.behandlingId,
                iverksettingId = dto.iverksettingId,
            )

            postgres.transaction { con ->
                oppdragService.hentStatusForOppdrag(oppdragId, con)
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
            call.respond(
                HttpStatusCode.NotFound,
                "Fant ikke oppdrag med id $dto",
            )
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.klarteIkkeSendeOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag) {
    call.respond(
        HttpStatusCode.InternalServerError,
        "Klarte ikke sende oppdrag for saksnr ${utbetalingsoppdrag.saksnummer}",
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.oppdragAlleredeSendt(utbetalingsoppdrag: Utbetalingsoppdrag) {
    call.respond(
        HttpStatusCode.Conflict,
        "Oppdrag er allerede sendt for saksnr ${utbetalingsoppdrag.saksnummer}",
    )
}

private fun opprettOppdrag(
    oppdragService: OppdragService,
    utbetalingsoppdrag: Utbetalingsoppdrag,
    versjon: Int,
) {
    val oppdrag110 = OppdragMapper.tilOppdrag110(utbetalingsoppdrag)
    val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)

    oppdragService.opprettOppdrag(utbetalingsoppdrag, oppdrag, versjon)
}
