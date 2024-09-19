package utsjekk.task.strategies

import TestRuntime
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

        TestRuntime.oppdrag.avstemmingRespondWith(avstemming.fagsystem, HttpStatusCode.Created)
        val actual = TestRuntime.oppdrag.awaitAvstemming(Fagsystem.DAGPENGER)

        val expected = avstemming.copy(
            til = avstemming.til.toLocalDate().atStartOfDay()
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `COMPLETED avstemming task oppretter ny task for neste virkedag`() = runTest {
        withContext(TestRuntime.context) {
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

            TestRuntime.oppdrag.avstemmingRespondWith(Fagsystem.DAGPENGER, HttpStatusCode.Created)

            transaction {
                task.insert()
            }

            repeatUntil(
                context = TestRuntime.context,
                function = ::getTask,
                predicate = { it?.status == Status.COMPLETE },
            )
        }
    }
}