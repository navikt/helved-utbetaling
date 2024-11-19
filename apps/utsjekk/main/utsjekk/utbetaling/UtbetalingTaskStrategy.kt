package utsjekk.utbetaling

import utsjekk.task.TaskStrategy
import utsjekk.task.Kind
import utsjekk.task.TaskDto
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class UtbetalingTaskStrategy(

): TaskStrategy {
    override suspend fun isApplicable(task: TaskDao): Boolean {
        return task.kind == libs.task.Kind.Utbetaling
    }

    override suspend fun execute(task: TaskDao) {
        val oppdrag = objectMapper.readValue<UtbetalingsoppdragDto>(task.payload)

        // todo: oppdrag

        Tasks.update(task.id, libs.task.Status.COMPLETE, "") {
            TaskDto.exponentialSec(it)
        }
    }

    companion object {
        fun metadataStrategy(payload: String): Map<String, String> {
            val utbetaling = objectMapper.readValue<Utbetaling>(payload)
            return mapOf(
                "sakId" to utbetaling.sakId.id,
                "behandlingId" to utbetaling.behandlingId.id,
                "iverksettingId" to null.toString(),
                "fagsystem" to utbetaling.stønad.asFagsystemStr()
            )
        }
    }
}

