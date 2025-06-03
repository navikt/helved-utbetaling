package models

import java.time.LocalDate

data class StatusReply(
    val status: Status, 
    val error: ApiError? = null,
    val detaljer: Detaljer? = null,
)

enum class Status {
    OK,
    FEILET,
    MOTTATT,
    HOS_OPPDRAG,
}

data class Detaljer(
    val linjer: List<DetaljerLinje>
)

data class DetaljerLinje(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val vedtakssats: UInt?,
    val klassekode: String,
)

