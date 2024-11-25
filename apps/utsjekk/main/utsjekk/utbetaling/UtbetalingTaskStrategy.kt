package utsjekk.utbetaling

import utsjekk.task.TaskStrategy
import utsjekk.task.Kind
import utsjekk.task.TaskDto
import utsjekk.clients.OppdragClient
import utsjekk.notFound
import libs.task.TaskDao
import libs.postgres.concurrency.transaction
import libs.task.Tasks
import libs.kafka.Kafka
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import com.fasterxml.jackson.module.kotlin.readValue

class UtbetalingTaskStrategy(
    private val oppdragClient: OppdragClient,
    private val statusProducer: Kafka<UtbetalingStatus>,
): TaskStrategy {
    override suspend fun isApplicable(task: TaskDao): Boolean {
        return task.kind == libs.task.Kind.Utbetaling
    }

    override suspend fun execute(task: TaskDao) {
        val oppdrag = objectMapper.readValue<UtbetalingsoppdragDto>(task.payload)

        iverksett(oppdrag)

        Tasks.update(task.id, libs.task.Status.COMPLETE, "") {
            TaskDto.exponentialSec(it)
        }
    }

    private suspend fun iverksett(oppdrag: UtbetalingsoppdragDto) {
        oppdragClient.iverksettOppdrag(oppdrag.into())

        transaction {
            val status = UtbetalingStatusDao.findOrNull(oppdrag.uid)
                ?: error("status for utbetaling {uid} mangler. Kan løses ved å manuelt legge inn en rad i utbetaling_status") 

            status.copy(data = status.data.copy(status = Status.SENDT_TIL_OPPDRAG))
                .update(oppdrag.uid)

            Tasks.create(
                kind = libs.task.Kind.StatusUtbetaling,
                payload = oppdrag.uid
            ) { uid ->
                uid.id.toString()
            }

            statusProducer.produce(oppdrag.uid.id.toString(), status.data)
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

