package utsjekk.avstemming

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import utsjekk.clients.OppdragClient
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import utsjekk.task.TaskStrategy
import utsjekk.task.Tasks
import java.time.LocalDate

class AvstemmingTaskStrategy(
    private val oppdrag: OppdragClient,
) : TaskStrategy {
    override suspend fun isApplicable(task: TaskDao): Boolean = task.kind == Kind.Avstemming

    override suspend fun execute(task: TaskDao) {
        val grensesnittavstemming =
            objectMapper
                .readValue<GrensesnittavstemmingRequest>(task.payload)
                .copy(
                    til = task.scheduledFor.toLocalDate().atStartOfDay(),
                )

        oppdrag.avstem(grensesnittavstemming)

        Tasks.update(task.id, Status.COMPLETE, "")

        val nesteGrensesnittavstemming =
            GrensesnittavstemmingRequest(
                fagsystem = grensesnittavstemming.fagsystem,
                fra = LocalDate.now().atStartOfDay(),
                til = LocalDate.now().nesteVirkedag().atStartOfDay(),
            )

        Tasks.create(
            Kind.Avstemming,
            nesteGrensesnittavstemming,
            scheduledFor = LocalDate.now().nesteVirkedag().atTime(8, 0),
        )
    }

    companion object {
        fun metadataStrategy(payload: String): Map<String, String> {
            val grensesnittavstemming = objectMapper.readValue<GrensesnittavstemmingRequest>(payload)
            return mapOf(
                "fagsystem" to grensesnittavstemming.fagsystem.name,
                "fra" to grensesnittavstemming.fra.toString(),
                "til" to grensesnittavstemming.til.toString(),
            )
        }
    }
}