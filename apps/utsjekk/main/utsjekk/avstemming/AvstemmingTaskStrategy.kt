package utsjekk.avstemming

import com.fasterxml.jackson.module.kotlin.readValue
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import utsjekk.clients.OppdragClient
import utsjekk.task.*
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

        val nesteGrensesnittavstemming =
            GrensesnittavstemmingRequest(
                fagsystem = grensesnittavstemming.fagsystem,
                fra = LocalDate.now().atStartOfDay(),
                til = LocalDate.now().nesteVirkedag().atStartOfDay(),
            )

        transaction {
            Tasks.update(task.id, Status.COMPLETE, "")
            Tasks.create(
                Kind.Avstemming,
                nesteGrensesnittavstemming,
                scheduledFor = LocalDate.now().nesteVirkedag().atTime(8, 0),
            )
        }
    }

    suspend fun initiserAvstemmingForNyeFagsystemer() {
        withLock("initiser manglende avstemming tasks") {
            val aktiveFagsystemer = transaction {
                TaskDao.select {
                    it.kind = Kind.Avstemming
                    it.status = listOf(Status.IN_PROGRESS)
                }
            }.map {
                objectMapper
                    .readValue<GrensesnittavstemmingRequest>(it.payload)
                    .fagsystem
            }

            Fagsystem.entries
                .filterNot { fagsystem -> aktiveFagsystemer.contains(fagsystem) }
                .forEach {
                    val avstemming = GrensesnittavstemmingRequest(
                        fagsystem = it,
                        fra = LocalDate.now().atStartOfDay(),
                        til = LocalDate.now().nesteVirkedag().atStartOfDay(),
                    )

                    Tasks.create(
                        Kind.Avstemming,
                        avstemming,
                        scheduledFor = LocalDate.now().nesteVirkedag().atTime(8, 0),
                    )
                }
        }
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
