package utsjekk.task

import java.time.LocalDateTime
import java.util.*

data class TaskDto(
    val id: UUID,
    val kind: Kind,
    val payload: String,
    val status: Status = Status.UNPROCESSED,
    val attempt: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
    val scheduledFor: LocalDateTime = createdAt.plusDays(1).withHour(8),
    val message: String? = null,
) {
    companion object {
        fun from(task: TaskDao) = TaskDto(
            id = task.id,
            kind = task.kind,
            payload = task.payload,
            status = task.status,
            attempt = task.attempt,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            scheduledFor = task.scheduledFor,
            message = task.message,
        )
    }
}

enum class Status {
    UNPROCESSED,
    COMPLETE,
    FAIL,
    PROCESSING,
    MANUAL;
}

enum class Kind {
    Avstemming,
    Iverksetting
}