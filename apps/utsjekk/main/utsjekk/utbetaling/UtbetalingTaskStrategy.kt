package utsjekk.utbetaling

import com.fasterxml.jackson.module.kotlin.readValue
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.objectMapper
import utsjekk.clients.Oppdrag
import utsjekk.task.TaskStrategy
import utsjekk.task.exponentialSec

class UtbetalingTaskStrategy(
    private val oppdragClient: Oppdrag,
    // private val statusProducer: Kafka<UtbetalingStatus>,
) : TaskStrategy {
    override suspend fun isApplicable(task: TaskDao): Boolean {
        return task.kind == libs.task.Kind.Utbetaling
    }

    override suspend fun execute(task: TaskDao) {
        val oppdrag = objectMapper.readValue<UtbetalingsoppdragDto>(task.payload)

        iverksett(oppdrag)

        Tasks.update(task.id, libs.task.Status.COMPLETE, "", TaskDao::exponentialSec)
    }

    private suspend fun iverksett(oppdrag: UtbetalingsoppdragDto) {
        oppdragClient.utbetal(oppdrag)

        transaction {
            val dao = UtbetalingDao.findOrNull(oppdrag.uid, oppdrag.erDelete())
                ?: error("utbetaling {uid} mangler. Kan løses ved å manuelt legge inn en rad i utbetaling")

            dao.copy(status = Status.SENDT_TIL_OPPDRAG).update(oppdrag.uid)

            Tasks.create(
                kind = libs.task.Kind.StatusUtbetaling,
                payload = oppdrag.uid
            ) { uid ->
                objectMapper.writeValueAsString(uid)
            }

            // statusProducer.produce(oppdrag.uid.id.toString(), status.data)
        }
    }

    private fun UtbetalingsoppdragDto.erDelete(): Boolean {
        return utbetalingsperioder.size == 1 && utbetalingsperioder.first().opphør?.fom == utbetalingsperioder.first().fom
    }

    companion object {
        fun metadataStrategy(payload: String): Map<String, String> {
            val utbetaling = objectMapper.readValue<UtbetalingsoppdragDto>(payload)
            return mapOf(
                "sakId" to utbetaling.saksnummer,
                "behandlingId" to utbetaling.utbetalingsperioder.maxBy { it.fom }.behandlingId,
                "iverksettingId" to null.toString(),
                "fagsystem" to utbetaling.fagsystem.name,
            )
        }
    }
}

