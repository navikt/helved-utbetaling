package utsjekk.task.strategies

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import utsjekk.avstemming.Virkedager
import utsjekk.oppdrag.OppdragClient
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import utsjekk.task.Tasks
import java.time.LocalDate

class AvstemmingStrategy(
    private val oppdrag: OppdragClient
) : TaskStrategy {
    override suspend fun execute(task: TaskDao) {
        val grensesnittavstemming = objectMapper.readValue<GrensesnittavstemmingRequest>(task.payload)
            .copy(
                til = task.scheduledFor.toLocalDate().atStartOfDay()
            )

        oppdrag.avstem(grensesnittavstemming)

        Tasks.update(task.id, Status.COMPLETE, "")

        val nesteGrensesnittavstemming = GrensesnittavstemmingRequest(
            fagsystem = grensesnittavstemming.fagsystem,
            fra = LocalDate.now().atStartOfDay(),
            til = Virkedager.neste().atStartOfDay(),
        )

        Tasks.create(
            Kind.Avstemming,
            nesteGrensesnittavstemming,
            scheduledFor = Virkedager.neste().atTime(8, 0)
        )
    }
}