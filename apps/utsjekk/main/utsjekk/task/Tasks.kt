package utsjekk.task

import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.objectMapper
import java.time.LocalDateTime
import java.util.*

object Tasks {

    suspend fun filterBy(status: List<Status>?, after: LocalDateTime?, kind: Kind?): List<TaskDto> =
        transaction {
            TaskDao.select {
                it.status = status
                it.createdAt = after?.let { SelectTime(Operator.GE, after) }
                it.kind = kind
            }
                .map(TaskDto::from)
        }

    suspend fun incomplete(): List<TaskDto> =
        transaction {
            TaskDao.select {
                it.status = Status.entries - Status.COMPLETE
            }.map(TaskDto::from)
        }

    suspend fun forKind(kind: Kind): List<TaskDto> =
        transaction {
            TaskDao.select {
                it.kind = kind
            }.map(TaskDto::from)
        }

    suspend fun forStatus(status: Status): List<TaskDto> =
        transaction {
            TaskDao.select {
                it.status = listOf(status)
            }.map(TaskDto::from)
        }

    suspend fun createdAfter(after: LocalDateTime): List<TaskDto> =
        transaction {
            TaskDao.select {
                it.createdAt = SelectTime(Operator.GE, after)
            }.map(TaskDto::from)
        }

    suspend fun update(id: UUID, status: Status, msg: String?) =
        transaction {
            val task = TaskDao.select {
                it.id = id
            }.single()
            // todo: skal vi øke scheduled for neste runde, eller skal den være til manuell håndtering?
            task.copy(
                status = status,
                updatedAt = LocalDateTime.now(),
                scheduledFor = task.kind.retryStrategy(task.attempt),
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

    suspend fun <T>create(kind: Kind, payload: T) {
        transaction {
            val now = LocalDateTime.now()
            TaskDao(
                id = UUID.randomUUID(),
                kind = kind,
                payload = objectMapper.writeValueAsString(payload),
                status = Status.UNPROCESSED,
                attempt = 0,
                message = null,
                createdAt = now,
                updatedAt = now,
                scheduledFor = now,
            ).insert()
        }
    }
}
