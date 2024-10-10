package utsjekk.task

import libs.task.TaskHistoryDao
import java.time.LocalDateTime
import java.util.*

data class TaskHistoryDto(
    val id: UUID,
    val taskId: UUID,
    val createdAt: LocalDateTime,
    val triggeredAt: LocalDateTime,
    val triggeredBy: LocalDateTime,
    val status: Status,
    val message: String?,
) {
    companion object {
        fun from(task: TaskHistoryDao) =
            TaskHistoryDto(
                id = task.id,
                taskId = task.taskId,
                createdAt = task.createdAt,
                triggeredAt = task.triggeredAt,
                triggeredBy = task.triggeredBy,
                status = Status.valueOf(task.status.name),
                message = task.message,
            )
    }
}