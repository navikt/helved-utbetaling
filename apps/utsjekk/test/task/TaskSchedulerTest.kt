package task

import TestRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettV2Dto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.*
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import java.time.LocalDateTime
import java.util.*

class TaskSchedulerTest {
    private val scope = CoroutineScope(TestRuntime.context)

    @Test
    fun `scheduled tasks are sent and set to completed`() = runTest(TestRuntime.context) {
        val iverksett = enIverksetting(TestData.dto.iverksetting())
        val resultat = enIverksettingResultat(iverksett)
        val json = objectMapper.writeValueAsString(iverksett.data)
        val task = aTask(json = json)

        populate(task, iverksett, resultat).await()
        assertEquals(Status.UNPROCESSED, getTask(task.id).await().single().status)

        while (!isComplete(task)) {
            runBlocking {
                delay(100)
            }
        }

        assertEquals(Status.COMPLETE, getTask(task.id).await().single().status)
    }

    private fun aTask(
        status: Status = Status.UNPROCESSED,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = createdAt,
        scheduledFor: LocalDateTime = createdAt,
        json: String,
    ) = TaskDao(
        id = UUID.randomUUID(),
        kind = Kind.Iverksetting,
        payload = json,
        status = status,
        attempt = 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
        scheduledFor = scheduledFor,
        message = null
    )

    private fun enIverksetting(dto: IverksettV2Dto) = IverksettingDao(
        behandlingId = dto.behandlingId.let(::BehandlingId),
        mottattTidspunkt = LocalDateTime.now(),
        data = Iverksetting.from(dto, Fagsystem.TILLEGGSSTØNADER)
    )

    private fun enIverksettingResultat(iverksetting: IverksettingDao) = IverksettingResultatDao(
        fagsystem = iverksetting.data.fagsak.fagsystem,
        sakId = iverksetting.data.sakId,
        behandlingId = iverksetting.behandlingId,
        iverksettingId = iverksetting.data.iverksettingId,
        tilkjentYtelseForUtbetaling = null,
        oppdragResultat = null
    )

    private fun populate(
        task: TaskDao,
        iverksetting: IverksettingDao,
        result: IverksettingResultatDao
    ): Deferred<Unit> =
        scope.async {
            transaction {
                task.insert()
                iverksetting.insert()
                result.insert()
            }
        }

    private suspend fun getTask(id: UUID): Deferred<List<TaskDao>> =
        scope.async {
            transaction {
                TaskDao.select {it.id = id }
            }
        }

    private suspend fun isComplete(task: TaskDao): Boolean =
        scope.async {
            transaction {
                TaskDao.select {
                    it.id = task.id
                    it.status = listOf(Status.COMPLETE)
                }.isNotEmpty()
            }
        }.await()

}