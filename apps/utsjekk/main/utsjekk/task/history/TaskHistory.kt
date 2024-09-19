package utsjekk.task.history

import libs.postgres.concurrency.transaction
import java.util.UUID

object TaskHistory {
    suspend fun history(id: UUID): List<TaskHistoryDto> =
        transaction {
            TaskHistoryDao.select(id)
                .map(TaskHistoryDto::from)
        }
}