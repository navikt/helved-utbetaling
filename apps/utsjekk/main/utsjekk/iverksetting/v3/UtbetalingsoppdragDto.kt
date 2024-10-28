package utsjekk.iverksetting.v3

import utsjekk.ApiError.Companion.badRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
}

fun String.tilFagsystem(): FagsystemDto = FagsystemDto.entries.find { it.kode == this }
    ?: badRequest("$this er ukjent fagsystem")

data class UtbetalingsoppdragDto(
    val erFørsteUtbetalingPåSak: Boolean,
    val fagsystem: FagsystemDto,
    val saksnummer: String,
    val aktør: String,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalingsperiode: List<UtbetalingsperiodeDto>,
    val avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    val brukersNavKontor: String? = null,
) {
    companion object
}

data class UtbetalingsperiodeDto(
    val erEndringPåEksisterendePeriode: Boolean,
    val id: UUID,
    val vedtaksdato: LocalDate,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: UInt,
    val satstype: Satstype,
    val utbetalesTil: String,
    val behandlingId: String,
    val opphør: Opphør? = null,
    val forrigeId: UUID? = null,
) {
    companion object
}

data class Opphør(val fom: LocalDate)
enum class Satstype { DAG, VIRKEDAG, MND, ENGANGS }
