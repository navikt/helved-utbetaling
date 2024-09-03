package utsjekk.task.strategies

import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import repeatUntil
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import utsjekk.task.Tasks
import java.time.LocalDateTime

class AvstemmingStrategyTest {

    @AfterEach
    fun reset() {
        TestRuntime.oppdrag.reset()
    }

    @Test
    fun `avstemming task sender grensesnittavstemming`() = runTest(TestRuntime.context) {
        val avstemming = GrensesnittavstemmingRequest(
            Fagsystem.DAGPENGER,
            fra = LocalDateTime.now(),
            til = LocalDateTime.now(),
        )

        Tasks.create(Kind.Avstemming, avstemming)

        val actual = TestRuntime.oppdrag.expectedAvstemming.await()

        val expected = avstemming.copy(
            til = avstemming.til.toLocalDate().atStartOfDay()
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `COMPLETED avstemming task oppretter ny task for neste virkedag`() = runTest(TestRuntime.context) {
        val now = LocalDateTime.now()
        val task = TaskDao(
            kind = Kind.Avstemming,
            payload =
            objectMapper.writeValueAsString(
                GrensesnittavstemmingRequest(
                    Fagsystem.DAGPENGER,
                    fra = LocalDateTime.now(),
                    til = LocalDateTime.now(),
                )
            ),
            status = Status.IN_PROGRESS,
            attempt = 0,
            message = null,
            createdAt = now,
            updatedAt = now,
            scheduledFor = now,
        )

        suspend fun getTask(): TaskDao? =
            transaction {
                TaskDao.select {
                    it.id = task.id
                }
            }.singleOrNull()

        transaction {
            task.insert()
        }

        repeatUntil(::getTask) {
            it?.status == Status.COMPLETE
        }
    }
}