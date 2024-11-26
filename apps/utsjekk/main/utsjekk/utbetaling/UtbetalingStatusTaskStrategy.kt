package utsjekk.utbetaling

import utsjekk.task.TaskStrategy
import utsjekk.task.Kind
import utsjekk.task.TaskDto
import utsjekk.task.exponentialMin
import utsjekk.clients.Oppdrag
import utsjekk.notFound
import libs.task.TaskDao
import libs.postgres.concurrency.transaction
import libs.task.Tasks
import libs.kafka.Kafka
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import com.fasterxml.jackson.module.kotlin.readValue
import utsjekk.appLog
import libs.utils.secureLog

class UtbetalingStatusTaskStrategy(
    private val oppdragClient: Oppdrag,
    private val statusProducer: Kafka<UtbetalingStatus>,
): TaskStrategy {

    override suspend fun isApplicable(task: TaskDao): Boolean {
        return task.kind == libs.task.Kind.StatusUtbetaling
    }

    override suspend fun execute(task: TaskDao) {
        val uId = objectMapper.readValue<UtbetalingId>(task.payload)

        val uDao = transaction {
            UtbetalingDao.findOrNull(uId)
        }

        if (uDao == null) {
            appLog.error("Fant ikke utbetaling $uId. Stopper status task ${task.id}")
            Tasks.update(task.id, libs.task.Status.FAIL, "fant ikke utbetaling med id $uId", TaskDao::exponentialMin)
        }

        uDao?.data?.let { utbetaling -> 
            val statusDto = oppdragClient.hentStatus(oppdragId(utbetaling))
            when (statusDto.status) {
                OppdragStatus.KVITTERT_OK -> {
                    Tasks.update(task.id, libs.task.Status.COMPLETE, "", TaskDao::exponentialMin)
                    val utbetalingStatus = insertOrUpdateStatus(uId, Status.OK)
                    statusProducer.produce(uId.id.toString(), utbetalingStatus)
                }
                OppdragStatus.KVITTERT_MED_MANGLER, OppdragStatus.KVITTERT_TEKNISK_FEIL, OppdragStatus.KVITTERT_FUNKSJONELL_FEIL -> { 
                    appLog.error("Mottok feilkvittering ${statusDto.status} fra OS for utbetaling $uId")
                    secureLog.error("Mottok feilkvittering ${statusDto.status} fra OS for utbetaling $uId. Feilmelding: ${statusDto.feilmelding}")
                    Tasks.update(task.id, libs.task.Status.MANUAL, statusDto.feilmelding, TaskDao::exponentialMin)
                    val utbetalingStatus = insertOrUpdateStatus(uId, Status.FEILET_MOT_OPPDRAG)
                    statusProducer.produce(uId.id.toString(), utbetalingStatus)
                }
                OppdragStatus.KVITTERT_UKJENT -> { 
                    appLog.error("Mottok ukjent kvittering fra OS for utbetaling $uId")
                    Tasks.update(task.id, libs.task.Status.MANUAL, statusDto.feilmelding, TaskDao::exponentialMin)
                    insertOrUpdateStatus(uId, Status.FEILET_MOT_OPPDRAG)
                }
                OppdragStatus.LAGT_PÅ_KØ -> { 
                    Tasks.update(task.id, task.status, null, TaskDao::exponentialMin)
                    insertOrUpdateStatus(uId, Status.SENDT_TIL_OPPDRAG)
                }
                OppdragStatus.OK_UTEN_UTBETALING -> { 
                    error("Status ${statusDto.status} skal aldri mottas fra utsjekk-oppdrag")
                }
            }
        }
    }
}

// private fun TaskDao.retry() = TaskDao::exponentialSec

private suspend fun insertOrUpdateStatus(
    uId: UtbetalingId,
    status: Status,
): UtbetalingStatus {
    return transaction {
        UtbetalingStatusDao.findOrNull(uId)
            ?.let { dao -> update(uId, dao, status) }
            ?: insert(uId, status)
    }
}

private suspend fun update(uId: UtbetalingId, dao: UtbetalingStatusDao, status: Status) : UtbetalingStatus {
    val uStatus = dao.data.copy(status = status)
    dao.copy(data = uStatus).update(uId)
    return uStatus
}

private suspend fun insert(uId: UtbetalingId, status: Status) : UtbetalingStatus {
    val uStatus = UtbetalingStatus(status)
    UtbetalingStatusDao(data = uStatus).insert(uId) 
    return uStatus
}

private fun oppdragId(u: Utbetaling): OppdragIdDto {
    return OppdragIdDto(
        fagsystem = fagsystem(u.stønad),
        sakId = u.sakId.id,
        behandlingId = u.behandlingId.id,
        iverksettingId = null,
    )
}

private fun fagsystem(stønad: Stønadstype): Fagsystem {
    return when (stønad) {
        is StønadTypeDagpenger -> Fagsystem.DAGPENGER
        is StønadTypeTiltakspenger -> Fagsystem.TILTAKSPENGER
        is StønadTypeTilleggsstønader -> Fagsystem.TILLEGGSSTØNADER
    }
}
