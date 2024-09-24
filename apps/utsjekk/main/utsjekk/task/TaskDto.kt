package utsjekk.task

import utsjekk.avstemming.AvstemmingTaskStrategy
import utsjekk.iverksetting.IverksettingTaskStrategy
import utsjekk.status.StatusTaskStrategy
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToLong

typealias RetryStrategy = (attemptNumber: Int) -> LocalDateTime

typealias MetadataStrategy = (payload: String) -> Map<String, String>

data class TaskDto(
    val id: UUID,
    val kind: Kind,
    val payload: String,
    val status: Status = Status.IN_PROGRESS,
    val attempt: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
    val scheduledFor: LocalDateTime = createdAt.plusDays(1).withHour(8),
    val message: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        fun from(task: TaskDao) =
            TaskDto(
                id = task.id,
                kind = task.kind,
                payload = task.payload,
                status = task.status,
                attempt = task.attempt,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                scheduledFor = task.scheduledFor,
                message = task.message,
                metadata = task.kind.metadataStrategy(task.payload),
            )

        val exponentialSec: RetryStrategy =
            { attemptNumber -> LocalDateTime.now().plusSeconds(2.0.pow(attemptNumber).roundToLong() + 10) }
        val exponentialMin: RetryStrategy =
            { attemptNumber ->
                if (attemptNumber <
                    5
                ) {
                    LocalDateTime.now().plusSeconds(2)
                } else {
                    LocalDateTime.now().plusMinutes(2.0.pow(attemptNumber).roundToLong() + 10)
                }
            }
        val constant: RetryStrategy = { LocalDateTime.now().plusSeconds(30) }
    }
}

enum class Status {
    IN_PROGRESS,
    COMPLETE,
    FAIL,
    MANUAL,
}

enum class Kind(
    val retryStrategy: RetryStrategy,
    val metadataStrategy: MetadataStrategy,
) {
    Avstemming(
        retryStrategy = TaskDto.constant,
        metadataStrategy = AvstemmingTaskStrategy::metadataStrategy,
    ),
    Iverksetting(
        retryStrategy = TaskDto.exponentialSec,
        metadataStrategy = IverksettingTaskStrategy::metadataStrategy,
    ),
    SjekkStatus(
        retryStrategy = TaskDto.exponentialMin,
        metadataStrategy = StatusTaskStrategy::metadataStrategy,
    ),
}
