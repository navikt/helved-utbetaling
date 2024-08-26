package utsjekk.task

import libs.postgres.concurrency.transaction
import java.time.LocalDateTime
import java.util.*

object Tasks {

    suspend fun filterBy(status: List<Status>?, after: LocalDateTime?, kind: Kind?): List<TaskDto> =
        transaction {
            TaskDao.select(
                TaskDao.Where(
                    status = status,
                    createdAt = after?.let { SelectTime(Operator.GE, it) },
                    kind = kind,
                )
            )
                .map(TaskDto::from)
        }

    suspend fun incomplete(): List<TaskDto> =
        transaction {
            TaskDao.select(
                TaskDao.Where(status = Status.entries - Status.COMPLETE)
            )
                .map(TaskDto::from)
        }

    suspend fun forKind(kind: Kind): List<TaskDto> =
        transaction {
            TaskDao.select(
                TaskDao.Where(kind = kind)
            )
                .map(TaskDto::from)
        }

    suspend fun forStatus(status: Status): List<TaskDto> =
        transaction {
            TaskDao.select(
                TaskDao.Where(status = listOf(status))
            )
                .map(TaskDto::from)
        }

    suspend fun createdAfter(after: LocalDateTime): List<TaskDto> =
        transaction {
            TaskDao.select(
                TaskDao.Where(createdAt = SelectTime(Operator.GE, after))
            )
                .map(TaskDto::from)
        }

    suspend fun update(id: UUID, status: Status, msg: String?) =
        transaction {
            val task = TaskDao.select(
                TaskDao.Where(id = id)
            ).single()
            task.copy(
                status = status,
                updatedAt = LocalDateTime.now(),
                attempt = task.attempt + 1,
                message = msg
            ).update()

            TaskHistoryDao(
                taskId = task.id,
                createdAt = task.createdAt,
                triggeredAt = task.updatedAt,
                triggeredBy = task.updatedAt,
                status = task.status
            ).insert()
        }
}
