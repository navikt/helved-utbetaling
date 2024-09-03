package utsjekk.task.strategies

import com.fasterxml.jackson.module.kotlin.readValue
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.iverksetting.BehandlingId
import utsjekk.iverksetting.IverksettingId
import utsjekk.iverksetting.IverksettingResultater
import utsjekk.iverksetting.OppdragResultat
import utsjekk.iverksetting.SakId
import utsjekk.iverksetting.UtbetalingId
import utsjekk.oppdrag.OppdragClient
import utsjekk.task.Status
import utsjekk.task.TaskDao
import utsjekk.task.Tasks

class SjekkStatusStrategy(private val oppdragClient: OppdragClient) : TaskStrategy {
    override suspend fun execute(task: TaskDao) {
        val oppdragIdDto = objectMapper.readValue<OppdragIdDto>(task.payload)
        val status = oppdragClient.hentStatus(oppdragIdDto)

        when (status.status) {
            OppdragStatus.KVITTERT_OK -> {
                IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
                Tasks.update(task.id, Status.COMPLETE, "")
            }
            OppdragStatus.KVITTERT_MED_MANGLER, OppdragStatus.KVITTERT_TEKNISK_FEIL, OppdragStatus.KVITTERT_FUNKSJONELL_FEIL -> {
                IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
                appLog.error("Mottok feilkvittering ${status.status} fra OS for oppdrag $oppdragIdDto")
                secureLog.error("Mottok feilkvittering ${status.status} fra OS for oppdrag $oppdragIdDto. Feilmelding: ${status.feilmelding}")
                Tasks.update(task.id, Status.MANUAL, status.feilmelding)
            }
            OppdragStatus.KVITTERT_UKJENT -> {
                IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
                appLog.error("Mottok ukjent kvittering fra OS for oppdrag $oppdragIdDto")
                Tasks.update(task.id, Status.MANUAL, "Ukjent kvittering fra OS")
            }
            OppdragStatus.LAGT_PÅ_KØ -> {
                Tasks.update(task.id, task.status, null)
            }

            OppdragStatus.OK_UTEN_UTBETALING -> {
                error("Status ${status.status} skal aldri mottas fra utsjekk-oppdrag.")
            }
        }
    }
}

fun OppdragIdDto.tilUtbetalingId() = UtbetalingId(
    fagsystem = this.fagsystem,
    sakId = SakId(this.sakId),
    behandlingId = BehandlingId(this.behandlingId),
    iverksettingId = this.iverksettingId?.let { IverksettingId(it) }
)