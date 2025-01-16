package utsjekk.utbetaling

import com.fasterxml.jackson.module.kotlin.readValue
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.Tasks
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.appLog
import utsjekk.clients.Oppdrag
import utsjekk.task.TaskStrategy
import utsjekk.task.exponentialMin

class UtbetalingStatusTaskStrategy(
    private val oppdragClient: Oppdrag,
    // private val statusProducer: Kafka<UtbetalingStatus>,
) : TaskStrategy {

    override suspend fun isApplicable(task: TaskDao): Boolean {
        return task.kind == libs.task.Kind.StatusUtbetaling
    }

    override suspend fun execute(task: TaskDao) {
        val uId = objectMapper.readValue<UtbetalingId>(task.payload)

        val uDao = transaction {
            UtbetalingDao.findOrNull(uId, withHistory = true)
        }

        if (uDao == null) {
            appLog.error("Fant ikke utbetaling $uId. Stopper status task ${task.id}")
            Tasks.update(task.id, libs.task.Status.FAIL, "fant ikke utbetaling med id $uId", TaskDao::exponentialMin)
            return
        }

        val statusDto = oppdragClient.utbetalStatus(uId)
        when (statusDto.status) {
            OppdragStatus.KVITTERT_OK -> {
                Tasks.update(task.id, libs.task.Status.COMPLETE, "", TaskDao::exponentialMin)
//                val utbetalingStatus = insertOrUpdateStatus(uId, Status.OK)
                // statusProducer.produce(uId.id.toString(), utbetalingStatus)
            }

            OppdragStatus.KVITTERT_MED_MANGLER, OppdragStatus.KVITTERT_TEKNISK_FEIL, OppdragStatus.KVITTERT_FUNKSJONELL_FEIL -> {
                appLog.error("Mottok feilkvittering ${statusDto.status} fra OS for utbetaling $uId")
                secureLog.error("Mottok feilkvittering ${statusDto.status} fra OS for utbetaling $uId. Feilmelding: ${statusDto.feilmelding}")
                Tasks.update(task.id, libs.task.Status.MANUAL, statusDto.feilmelding, TaskDao::exponentialMin)
//                val utbetalingStatus = insertOrUpdateStatus(uId, Status.FEILET_MOT_OPPDRAG)
                // statusProducer.produce(uId.id.toString(), utbetalingStatus)
            }

            OppdragStatus.KVITTERT_UKJENT -> {
                appLog.error("Mottok ukjent kvittering fra OS for utbetaling $uId")
                Tasks.update(task.id, libs.task.Status.MANUAL, statusDto.feilmelding, TaskDao::exponentialMin)
                insertOrUpdateStatus(uId, Status.FEILET_MOT_OPPDRAG)
            }

            OppdragStatus.LAGT_PÅ_KØ -> {
                Tasks.update(task.id, libs.task.Status.IN_PROGRESS, null, TaskDao::exponentialMin)
                insertOrUpdateStatus(uId, Status.SENDT_TIL_OPPDRAG)
            }

            OppdragStatus.OK_UTEN_UTBETALING -> {
                error("Status ${statusDto.status} skal aldri mottas fra utsjekk-oppdrag")
            }
        }
    }

    companion object {
        fun metadataStrategy(payload: String): Map<String, String> {
            val uid = objectMapper.readValue<UtbetalingId>(payload)
            return mapOf(
                "utbetalingId" to uid.id.toString(),
            )
        }
    }
}

private suspend fun insertOrUpdateStatus(
    uId: UtbetalingId,
    status: Status,
): UtbetalingStatus {
    return transaction {
        UtbetalingStatusDao.findOrNull(uId)
            ?.let { dao -> utled(uId, dao, status) }
            ?: insert(uId, status)
    }
}

private suspend fun utled(uId: UtbetalingId, dao: UtbetalingStatusDao, status: Status): UtbetalingStatus {
    val uStatus = dao.data.copy(status = status)
    dao.copy(data = uStatus).update(uId)
    return uStatus
}

private suspend fun insert(uId: UtbetalingId, status: Status): UtbetalingStatus {
    val uStatus = UtbetalingStatus(status)
    UtbetalingStatusDao(data = uStatus).insert(uId)
    return uStatus
}
