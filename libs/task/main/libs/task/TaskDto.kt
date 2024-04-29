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
    val id: Long,
    val antallLogger: Int,
    val avvikstype: Avvikstype?,
    val callId: String,
    val kommentar: String?,
    val metadata: Properties,
    val opprettetTidspunkt: LocalDateTime,
    val payload: String,
    val sistKjørt: LocalDateTime?,
    val status: Status = Status.UBEHANDLET,
    val taskStepType: String,
    val triggerTid: LocalDateTime?,
)

data class TaskloggDto(
    val id: Long,
    val endretAv: String?,
    val melding: String?,
    val node: String,
    val opprettetTidspunkt: LocalDateTime,
    val type: Loggtype,
)

enum class Loggtype {
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
}

enum class Avvikstype {
    ANNET,
    DUPLIKAT
}
