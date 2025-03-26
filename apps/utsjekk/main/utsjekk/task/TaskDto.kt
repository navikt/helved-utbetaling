package utsjekk.task

import libs.task.TaskDao
import utsjekk.avstemming.erHelligdag
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import com.fasterxml.jackson.module.kotlin.readValue
// import utsjekk.iverksetting.IverksettingTaskStrategy
// import utsjekk.status.StatusTaskStrategy
// import utsjekk.utbetaling.UtbetalingTaskStrategy
// import utsjekk.utbetaling.UtbetalingStatusTaskStrategy
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
    Avstemming(metadataStrategy = TaskMetadataStrategy::avstemming),
    Iverksetting(metadataStrategy = TaskMetadataStrategy::iverksetting),
    Utbetaling(metadataStrategy = TaskMetadataStrategy::utbetaling),
    StatusUtbetaling(metadataStrategy = TaskMetadataStrategy::utbetalingStatus),
    SjekkStatus(metadataStrategy = TaskMetadataStrategy::status),
}

private object TaskMetadataStrategy {
    fun iverksetting(payload: String): Map<String, String> {
        val iverksetting = objectMapper.readValue<utsjekk.iverksetting.Iverksetting>(payload)
        return mapOf(
            "sakId" to iverksetting.fagsak.fagsakId.id,
            "behandlingId" to iverksetting.behandling.behandlingId.id,
            "iverksettingId" to iverksetting.behandling.iverksettingId?.id.toString(),
            "fagsystem" to iverksetting.fagsak.fagsystem.name,
        )
    }
    fun avstemming(payload: String): Map<String, String> {
        val grensesnittavstemming = objectMapper.readValue<GrensesnittavstemmingRequest>(payload)
        return mapOf(
            "fagsystem" to grensesnittavstemming.fagsystem.name,
            "fra" to grensesnittavstemming.fra.toString(),
            "til" to grensesnittavstemming.til.toString(),
        )
    }
    fun utbetaling(payload: String): Map<String, String> {
        val utbetaling = objectMapper.readValue<utsjekk.utbetaling.UtbetalingsoppdragDto>(payload)
        return mapOf(
            "sakId" to utbetaling.saksnummer,
            "behandlingId" to utbetaling.utbetalingsperioder.maxBy { it.fom }.behandlingId,
            "iverksettingId" to null.toString(),
            "fagsystem" to utbetaling.fagsystem.name,
        )
    }
    fun utbetalingStatus(payload: String): Map<String, String> {
        val uid = objectMapper.readValue<utsjekk.utbetaling.UtbetalingId>(payload)
        return mapOf(
            "utbetalingId" to uid.id.toString(),
        )
    }
    fun status(payload: String): Map<String, String> {
        val oppdragIdDto = objectMapper.readValue<no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto>(payload)
        return mapOf(
            "sakId" to oppdragIdDto.sakId,
            "behandlingId" to oppdragIdDto.behandlingId,
            "iverksettingId" to oppdragIdDto.iverksettingId.toString(),
            "fagsystem" to oppdragIdDto.fagsystem.name,
        )
    }
}


