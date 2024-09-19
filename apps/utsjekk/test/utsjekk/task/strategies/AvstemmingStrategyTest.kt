package utsjekk.task.strategies

import TestRuntime
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.task.*
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

        assertEquals(avstemming.til.toLocalDate().atStartOfDay(), actual.til)
//        assertEquals(avstemming.fra.truncatedTo(ChronoUnit.SECONDS), actual.til.truncatedTo(ChronoUnit.SECONDS))
        assertEquals(Fagsystem.DAGPENGER, actual.fagsystem)
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

        TestRuntime.oppdrag.avstemmingRespondWith(Fagsystem.DAGPENGER, HttpStatusCode.Created)

        transaction {
            task.insert()
        }

        val actual = runBlocking {
            suspend fun getTask(attempt: Int): TaskDto? {
                return withContext(TestRuntime.context) {
                    val actual = transaction {
                        Tasks.forId(task.id)
                    }
                    if (actual?.status != Status.COMPLETE && attempt < 1000) getTask(attempt + 1)
                    else actual
                }
            }
            getTask(0)
        }

        assertEquals(Status.COMPLETE, actual?.status)
    }
}