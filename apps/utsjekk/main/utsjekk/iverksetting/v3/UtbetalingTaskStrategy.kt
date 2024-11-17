package utsjekk.iverksetting.v3

import utsjekk.task.TaskStrategy
import utsjekk.task.Kind
import utsjekk.task.TaskDto
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class UtbetalingTaskStategy(

): TaskStrategy {
    override suspend fun isApplicable(task: TaskDao): Boolean {
        return task.kind == libs.task.Kind.Utbetaling
    }

    override suspend fun execute(task: TaskDao) {
        val utbetaling = objectMapper.readValue<Utbetaling>(task.payload)
        val fagsystem = FagsystemDto.valueOf(utbetaling.stønad.asFagsystemStr())

        // TODO: vi har mistet CRUD-konteksten her
        val dto = UtbetalingsoppdragService.create(utbetaling, fagsystem)

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

