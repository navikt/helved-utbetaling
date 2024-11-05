package utsjekk.task

import libs.task.TaskDao
import utsjekk.avstemming.AvstemmingTaskStrategy
import utsjekk.iverksetting.IverksettingTaskStrategy
import utsjekk.status.StatusTaskStrategy
import java.time.LocalDateTime
import java.util.*
import kotlin.math.min
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
        private const val MINUTES_PER_DAY: Long = 24 * 60
        private const val SECONDS_PER_DAY: Long = MINUTES_PER_DAY * 60

        fun from(task: TaskDao) =
            TaskDto(
                id = task.id,
                kind = Kind.valueOf(task.kind.name),
                payload = task.payload,
                status = Status.valueOf(task.status.name),
                attempt = task.attempt,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                scheduledFor = task.scheduledFor,
                message = task.message,
                metadata = Kind.valueOf(task.kind.name).metadataStrategy(task.payload),
            )

        val exponentialSec: RetryStrategy =
            { attemptNumber -> LocalDateTime.now().plusSeconds(min(2.0.pow(attemptNumber).roundToLong() + 10, SECONDS_PER_DAY)) }
        val exponentialMin: RetryStrategy =
            { attemptNumber ->
                if (attemptNumber < 5) {
                    LocalDateTime.now().plusSeconds(10)
                } else {
                    LocalDateTime.now().plusMinutes(min(2.0.pow(attemptNumber).roundToLong() + 10, MINUTES_PER_DAY))
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
