package libs.task

import java.time.LocalDateTime
import java.util.*

data class Ressurs<T>(
    val data: T?,
    val status: Status,
    val melding: String,
    val frontendFeilmelding: String? = null,
) {

    enum class Status {
        SUKSESS,
        FEILET,
    }

    companion object {
        fun <T> success(data: T): Ressurs<T> = Ressurs(
            data = data,
            status = Status.SUKSESS,
            melding = "Innhenting av data var vellykket",
        )

        fun <T> failure(
            errorMessage: String? = null,
            error: Throwable? = null,
        ): Ressurs<T> = Ressurs(
            data = null,
            status = Status.FEILET,
            melding = errorMessage ?: "En feil har oppstått: ${error?.message}",
            frontendFeilmelding = errorMessage ?: "En feil har oppstått: ${error?.message}",
        )
    }

    override fun toString(): String {
        return "Ressurs(status=$status, melding='$melding')"
    }
}

data class TaskerMedStatusFeiletOgManuellOppfølging(
    val antallFeilet: Long,
    val antallManuellOppfølging: Long
)

data class KommentarDTO(
    val settTilManuellOppfølging: Boolean,
    val kommentar: String,
)

data class AvvikshåndterDTO(
    val avvikstype: Avvikstype,
    val årsak: String,
)

data class TaskDto(
    val id: UUID,
    val antallLogger: Int,
    val avvikstype: Avvikstype?,
    val kommentar: String?,
    val metadata: String?,
    val opprettetTidspunkt: LocalDateTime,
    val payload: String,
    val sistKjørt: LocalDateTime?,
    val status: Status = Status.UBEHANDLET,
    val taskStepType: String,
    val triggerTid: LocalDateTime?,
) {
    companion object {
        fun from(task: TaskDao, taskLog: TaskLogMetadata?) = TaskDto(
            id = task.id,
            status = task.status,
            avvikstype = task.avvikstype?.let(Avvikstype::valueOf),
            opprettetTidspunkt = task.opprettet_tid,
            triggerTid = task.trigger_tid,
            taskStepType = task.type,
            metadata = task.metadata,
            payload = task.payload,
            antallLogger = taskLog?.antall_logger ?: 0,
            sistKjørt = taskLog?.siste_opprettet_tid,
            kommentar = taskLog?.siste_kommentar,
        )
    }
}

data class TaskLogDto(
    val id: Long,
    val endretAv: String?,
    val melding: String?,
    val node: String,
    val opprettetTidspunkt: LocalDateTime,
    val type: LogType,
)

enum class LogType {
    AVVIKSHÅNDTERT,
    BEHANDLER,
    FEILET,
    FERDIG,
    KLAR_TIL_PLUKK,
    KOMMENTAR,
    MANUELL_OPPFØLGING,
    PLUKKET,
    UBEHANDLET,
}

enum class Status {
    AVVIKSHÅNDTERT,
    BEHANDLER,
    FEILET,
    FERDIG,
    KLAR_TIL_PLUKK,
    MANUELL_OPPFØLGING,
    PLUKKET,
    UBEHANDLET;

    fun kanPlukkes() = listOf(KLAR_TIL_PLUKK, UBEHANDLET).contains(this)

    companion object {
        fun open(): List<Status> = listOf(
            UBEHANDLET,
            BEHANDLER,
            PLUKKET,
            KLAR_TIL_PLUKK
        )
    }
}

enum class Avvikstype {
    ANNET,
    DUPLIKAT
}
