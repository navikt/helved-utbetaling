package task

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import java.time.LocalDateTime
import java.util.*

class TaskSchedulerTest {
    private val scope = CoroutineScope(TestRuntime.context)

    @Test
    fun `scheduled tasks are sent and set to completed`() = runTest(TestRuntime.context) {
        val task = aTask()

        populate(task).await()
        Assertions.assertEquals(Status.UNPROCESSED, getTask(task.id).await().single().status)

        while (!isComplete(task)) {
            runBlocking {
                delay(100)
            }
        }

        Assertions.assertEquals(Status.COMPLETE, getTask(task.id).await().single().status)
    }

    private fun aTask(
        status: Status = Status.UNPROCESSED,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = createdAt,
        scheduledFor: LocalDateTime = createdAt,
    ) = TaskDao(
        id = UUID.randomUUID(),
        kind = Kind.Iverksetting,
        payload = "hello world",
        status = status,
        attempt = 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
        scheduledFor = scheduledFor,
        message = null
    )

    private fun populate(task: TaskDao): Deferred<Unit> =
        scope.async {
            transaction {
                task.insert()
            }
        }

    private suspend fun getTask(id: UUID): Deferred<List<TaskDao>> =
        scope.async {
            transaction {
                TaskDao.select(id)
            }
        }

    private suspend fun isComplete(task: TaskDao): Boolean =
        scope.async {
            transaction {
                TaskDao.select(id = task.id, status = listOf(Status.COMPLETE)).isNotEmpty()
            }
        }.await()

}