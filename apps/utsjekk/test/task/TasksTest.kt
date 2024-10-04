package task

import TestData
import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.postgres.Postgres
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.objectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import utsjekk.task.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals

class TasksTest {
    @BeforeEach
    fun cleanup() {
        TestRuntime.clear(TaskDao.TABLE_NAME)
    }

    @Nested
    inner class incomplete {
        @Test
        fun `excludes completed`() =
            runTest(Postgres.context) {
                transaction {
                    enTask(Status.COMPLETE).insert()
                }
                assertEquals(0, Tasks.incomplete().size)
            }

        @Test
        fun `includes in progress`() =
            runTest(Postgres.context) {
                transaction {
                    enTask(Status.IN_PROGRESS).insert()
                }
                assertEquals(1, Tasks.incomplete().size)
            }

        @Test
        fun `includes manual`() =
            runTest(Postgres.context) {
                transaction {
                    enTask(Status.MANUAL).insert()
                }
                assertEquals(1, Tasks.incomplete().size)
            }

        @Test
        fun `includes fail`() =
            runTest(Postgres.context) {
                transaction {
                    enTask(Status.FAIL).insert()
                }
                assertEquals(1, Tasks.incomplete().size)
            }
    }

    @Nested
    inner class forStatus {
        @Test
        fun `filter selected`() =
            runTest(Postgres.context) {
                transaction {
                    Status.entries.forEach { status ->
                        enTask(status).insert()
                    }
                }

                Status.entries.forEach { status ->
                    val tasks = Tasks.forStatus(status)
                    assertEquals(1, tasks.size)
                    assertEquals(status, tasks.single().status)
                }
            }
    }

    @Nested
    inner class createdAfter {
        @Test
        fun `includes after`() =
            runTest(Postgres.context) {
                transaction {
                    enTask(createdAt = LocalDateTime.of(2024, 6, 14, 10, 45)).insert()
                    enTask(createdAt = LocalDateTime.of(2024, 6, 15, 10, 45)).insert()
                    enTask(createdAt = LocalDateTime.of(2024, 6, 16, 10, 45)).insert()
                }

                assertEquals(3, Tasks.createdAfter(LocalDateTime.of(2024, 6, 13, 10, 45)).size)
            }

        @Test
        fun `excludes before`() =
            runTest(Postgres.context) {
                transaction {
                    enTask(createdAt = LocalDateTime.of(2024, 6, 14, 10, 45)).insert()
                    enTask(createdAt = LocalDateTime.of(2024, 6, 15, 10, 45)).insert()
                    enTask(createdAt = LocalDateTime.of(2024, 6, 16, 10, 45)).insert()
                }
                assertEquals(0, Tasks.createdAfter(LocalDateTime.of(2024, 6, 17, 10, 45)).size)
            }

        @Test
        fun `includes limit`() =
            runTest(Postgres.context) {
                transaction {
                    enTask(createdAt = LocalDateTime.of(2024, 6, 14, 10, 45)).insert()
                    enTask(createdAt = LocalDateTime.of(2024, 6, 15, 10, 45)).insert()
                    enTask(createdAt = LocalDateTime.of(2024, 6, 16, 10, 45)).insert()
                }
                assertEquals(2, Tasks.createdAfter(LocalDateTime.of(2024, 6, 15, 10, 45)).size)
            }
    }

    @Nested
    inner class update {
        @Test
        fun `attempt is increased`() =
            runTest(Postgres.context) {
                val task =
                    transaction {
                        enTask(Status.IN_PROGRESS).apply { insert() }
                    }
                transaction {
                    Tasks.update(task.id, Status.MANUAL, "Klarer ikke automatisk sende inn oppdrag")
                }

                val actual = transaction { TaskDao.select { it.id = task.id } }.single()
                assertEquals(1, actual.attempt)
            }

        @Test
        fun `update_at is set to now`() =
            runTest(Postgres.context) {
                val task =
                    transaction {
                        enTask(Status.IN_PROGRESS).apply { insert() }
                    }
                transaction {
                    Tasks.update(task.id, Status.IN_PROGRESS, "Oppdrag var stengt. Forsøker igjen...")
                }

                val actual = transaction { TaskDao.select { it.id = task.id } }.single()
                assertTrue(task.updatedAt.isBefore(actual.updatedAt))
                assertTrue(LocalDateTime.now().isAfter(actual.updatedAt))
            }

        @Test
        fun `scheduled for is set according to retry strategy`() =
            runTest(Postgres.context) {
                val task =
                    transaction {
                        enTask(Status.IN_PROGRESS).apply { insert() }
                    }
                val expectedNextAttemptTime = TaskDto.exponentialSec(0)

                transaction {
                    Tasks.update(task.id, Status.IN_PROGRESS, "Oppdrag var stengt. Forsøker igjen...")
                }

                val updatedTask =
                    transaction {
                        TaskDao.select { it.id = task.id }
                    }.firstOrNull()

                assertWithin(expectedNextAttemptTime, updatedTask?.scheduledFor!!, 1)
            }

        @Test
        fun `message is applied`() =
            runTest(Postgres.context) {
                val task =
                    transaction {
                        enTask(Status.IN_PROGRESS).apply { insert() }
                    }
                transaction {
                    Tasks.update(task.id, Status.FAIL, "Ugyldig id")
                }

                val actual = transaction { TaskDao.select { it.id = task.id } }.single()
                assertEquals("Ugyldig id", actual.message)
            }
    }

    private fun assertWithin(
        expected: LocalDateTime,
        actual: LocalDateTime,
        seconds: Long,
    ) = listOf(
        expected.truncatedTo(ChronoUnit.SECONDS),
        expected.truncatedTo(ChronoUnit.SECONDS).plusSeconds(seconds),
    ).contains(actual.truncatedTo(ChronoUnit.SECONDS))
}

private fun enTask(
    status: Status = Status.IN_PROGRESS,
    createdAt: LocalDateTime = LocalDateTime.now(),
) = TaskDao(
    id = UUID.randomUUID(),
    kind = Kind.Iverksetting,
    payload = objectMapper.writeValueAsString(TestData.domain.iverksetting()),
    status = status,
    attempt = 0,
    createdAt = createdAt,
    updatedAt = createdAt,
    scheduledFor = createdAt,
    message = null,
)
