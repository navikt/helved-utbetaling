package task

import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import java.time.LocalDateTime
import java.util.*

class TaskDaoTest {
    private val task = enTask(Status.UNPROCESSED)

    @Test
    fun `can update status`() = runTest(TestRuntime.context) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(status = Status.COMPLETE).update()
        }

        val actual = transaction { TaskDao.select(TaskDao.Where( id = task.id)) }
        assertEquals(Status.COMPLETE, actual.single().status)
    }

    @Test
    fun `can update attempt`() = runTest(TestRuntime.context) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(attempt = task.attempt + 4).update()
        }

        val actual = transaction { TaskDao.select(TaskDao.Where(id = task.id)) }
        assertEquals(4, actual.single().attempt)
    }

    @Test
    fun `can update update_at`() = runTest(TestRuntime.context) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(updatedAt = LocalDateTime.of(2024, 5, 17, 23, 59)).update()
        }

        val actual = transaction { TaskDao.select(TaskDao.Where(id = task.id)) }
        assertEquals(LocalDateTime.of(2024, 5, 17, 23, 59), actual.single().updatedAt)
    }

    @Test
    fun `can update message`() = runTest(TestRuntime.context) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(message = "hello there").update()
        }

        val actual = transaction { TaskDao.select(TaskDao.Where(id = task.id)) }
        assertEquals("hello there", actual.single().message)
    }

    @Test
    fun `can update scheduled_for`() = runTest(TestRuntime.context) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(scheduledFor = LocalDateTime.of(2024, 5, 17, 23, 59)).update()
        }

        val actual = transaction { TaskDao.select(TaskDao.Where(id = task.id)) }
        assertEquals(LocalDateTime.of(2024, 5, 17, 23, 59), actual.single().scheduledFor)
    }

    @Test
    fun `can update status attempt updated_at`() = runTest(TestRuntime.context) {
        val now = LocalDateTime.now()
        val id = UUID.randomUUID()

        transaction {
            TaskDao(
                id = id,
                kind = Kind.Iverksetting,
                payload = "some payload",
                status = Status.UNPROCESSED,
                attempt = 0,
                createdAt = now,
                updatedAt = now,
                scheduledFor = now,
                message = null,
            ).insert()
        }

        val before = transaction { TaskDao.select(TaskDao.Where(id = id)) }.single()
        assertEquals(Status.UNPROCESSED, before.status)
        assertEquals(before.createdAt, before.updatedAt)
        assertEquals(0, before.attempt)
        assertEquals(null, before.message)

        transaction {
            before.copy(
                status = Status.FAIL,
                updatedAt = now.plusMinutes(1),
                attempt = task.attempt + 1,
                message = "Invalid payload"
            ).update()
        }

        val actual = transaction { TaskDao.select(TaskDao.Where(id = id)) }.single()
        assertEquals(Status.FAIL, actual.status)
        assertTrue(before.updatedAt.isBefore(actual.updatedAt))
        assertEquals(1, actual.attempt)
        assertEquals("Invalid payload", actual.message)
    }
}

private fun enTask(
    status: Status = Status.UNPROCESSED,
    createdAt: LocalDateTime = LocalDateTime.now(),
) = TaskDao(
    id = UUID.randomUUID(),
    kind = Kind.Iverksetting,
    payload = "some payload",
    status = status,
    attempt = 0,
    createdAt = createdAt,
    updatedAt = createdAt,
    scheduledFor = createdAt,
    message = null,
)
