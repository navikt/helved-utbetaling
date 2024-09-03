package utsjekk.task

import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToLong

typealias RetryStrategy = (attemptNumber: Int) -> LocalDateTime

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

        val exponentialSec: RetryStrategy =
            { attemptNumber -> LocalDateTime.now().plusSeconds(2.0.pow(attemptNumber).roundToLong() + 10) }
        val exponentialMin: RetryStrategy =
            { attemptNumber -> if (attemptNumber < 5) LocalDateTime.now().plusSeconds(2) else LocalDateTime.now().plusMinutes(2.0.pow(attemptNumber).roundToLong() + 10) }
        val constant: RetryStrategy = { LocalDateTime.now().plusSeconds(30) }
    }
}

enum class Status {
    UNPROCESSED,
    COMPLETE,
    FAIL,
    PROCESSING,
    MANUAL;
}

enum class Kind(val retryStrategy: RetryStrategy) {
    Avstemming(retryStrategy = TaskDto.constant),
    Iverksetting(retryStrategy = TaskDto.exponentialSec),
    SjekkStatus(retryStrategy = TaskDto.exponentialMin);
}
