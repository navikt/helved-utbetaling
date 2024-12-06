package utsjekk.task

import libs.task.TaskDao
import utsjekk.avstemming.AvstemmingTaskStrategy
import utsjekk.avstemming.erHelligdag
import utsjekk.iverksetting.IverksettingTaskStrategy
import utsjekk.status.StatusTaskStrategy
import utsjekk.utbetaling.UtbetalingTaskStrategy
import utsjekk.utbetaling.UtbetalingStatusTaskStrategy
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

typealias MetadataStrategy = (payload: String) -> Map<String, String>

private const val MINUTES_PER_DAY: Long = 24 * 60
private const val SECONDS_PER_DAY: Long = MINUTES_PER_DAY * 60

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
    }
}

fun TaskDao.exponentialSec(): LocalDateTime = 
    LocalDateTime.now().plusSeconds(min(2.0.pow(attempt).roundToLong() + 10, SECONDS_PER_DAY))

fun TaskDao.constant10Sec(): LocalDateTime = LocalDateTime.now().plusSeconds(10)

fun TaskDao.exponentialMin(): LocalDateTime = when (attempt) {
    0, 1, 2, 3, 4, 5 -> constant10Sec()
    else -> LocalDateTime.now().plusMinutes(min(2.0.pow(attempt).roundToLong() + 10, MINUTES_PER_DAY))
}

fun TaskDao.exponentialMinAccountForWeekendsAndPublicHolidays(): LocalDateTime =
    if (this.scheduledFor.toLocalDate().erHelligdag()) {
        this.scheduledFor.plusDays(1).withHour(8).withMinute(0)
    } else {
        this.exponentialMin()
    }

enum class Status {
    IN_PROGRESS,
    COMPLETE,
    FAIL,
    MANUAL,
T}

enum class Kind(
    val metadataStrategy: MetadataStrategy,
) {
    Avstemming(metadataStrategy = AvstemmingTaskStrategy::metadataStrategy),
    Iverksetting(metadataStrategy = IverksettingTaskStrategy::metadataStrategy),
    Utbetaling(metadataStrategy = UtbetalingTaskStrategy::metadataStrategy),
    StatusUtbetaling(metadataStrategy = UtbetalingStatusTaskStrategy::metadataStrategy),
    SjekkStatus(metadataStrategy = StatusTaskStrategy::metadataStrategy),
}
