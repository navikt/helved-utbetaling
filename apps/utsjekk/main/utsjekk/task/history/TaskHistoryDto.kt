package utsjekk.task.history

import utsjekk.task.Status
import java.time.LocalDateTime
import java.util.UUID

data class TaskHistoryDto(
    val id: UUID,
    val taskId: UUID,
    val createdAt: LocalDateTime,
    val triggeredAt: LocalDateTime,
    val triggeredBy: LocalDateTime,
    val status: Status,
) {
    companion object {
        fun from(task: TaskHistoryDao) = TaskHistoryDto(
            id = task.id,
            taskId = task.taskId,
            createdAt = task.createdAt,
            triggeredAt = task.triggeredAt,
            triggeredBy = task.triggeredBy,
            status = task.status
        )
    }
}
