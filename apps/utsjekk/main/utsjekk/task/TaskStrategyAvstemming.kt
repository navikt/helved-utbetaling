package utsjekk.task

import com.fasterxml.jackson.module.kotlin.readValue
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.task.Kind
import libs.task.Status
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import utsjekk.avstemming.nesteVirkedag
//import utsjekk.clients.Oppdrag
import java.time.LocalDate

//class TaskStrategyAvstemming(private val oppdrag: Oppdrag) : TaskStrategy {
//    override suspend fun isApplicable(task: TaskDao): Boolean = task.kind == libs.task.Kind.Avstemming
//
//    override suspend fun execute(task: TaskDao) {
//        val grensesnittavstemming =
//            objectMapper
//                .readValue<GrensesnittavstemmingRequest>(task.payload)
//                .copy(
//                    til = task.scheduledFor.toLocalDate().atStartOfDay(),
//                )
//
//        oppdrag.avstem(grensesnittavstemming)
//
//        val nesteGrensesnittavstemming =
//            GrensesnittavstemmingRequest(
//                fagsystem = grensesnittavstemming.fagsystem,
//                fra = LocalDate.now().atStartOfDay(),
//                til = LocalDate.now().nesteVirkedag().atStartOfDay(),
//            )
//
//        transaction {
//            Tasks.update(task.id, Status.COMPLETE, "", TaskDao::exponentialSec)
//            Tasks.create(
//                Kind.Avstemming,
//                nesteGrensesnittavstemming,
//                scheduledFor = LocalDate.now().nesteVirkedag().atTime(8, 0),
//            ) {
//                objectMapper.writeValueAsString(it)
//            }
//        }
//    }
//
//    suspend fun initiserAvstemmingForNyeFagsystemer() {
//        withLock("initiser manglende avstemming tasks") {
//            val aktiveFagsystemer = transaction {
//                TaskDao.select {
//                    it.kind = listOf(Kind.Avstemming)
//                    it.status = listOf(Status.IN_PROGRESS)
//                }
//            }.map {
//                objectMapper
//                    .readValue<GrensesnittavstemmingRequest>(it.payload)
//                    .fagsystem
//            }
//
//            Fagsystem.entries
//                .filterNot { fagsystem -> aktiveFagsystemer.contains(fagsystem) }
//                .forEach {
//                    val avstemming = GrensesnittavstemmingRequest(
//                        fagsystem = it,
//                        fra = LocalDate.now().atStartOfDay(),
//                        til = LocalDate.now().nesteVirkedag().atStartOfDay(),
//                    )
//
//                    Tasks.create(
//                        Kind.Avstemming,
//                        avstemming,
//                        scheduledFor = LocalDate.now().nesteVirkedag().atTime(8, 0),
//                    ) {
//                        objectMapper.writeValueAsString(it)
//                    }
//                }
//        }
//    }
//}