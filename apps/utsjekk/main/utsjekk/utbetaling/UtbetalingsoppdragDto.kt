package utsjekk.utbetaling

import utsjekk.badRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST");

    companion object {
        fun from(stønad: Stønadstype): FagsystemDto {
            return FagsystemDto.entries
                .find { it.name == stønad.asFagsystemStr() }
                ?: badRequest("$stønad er ukjent fagsystem")
        }
    }
}

data class UtbetalingsoppdragDto(
    val erFørsteUtbetalingPåSak: Boolean,
    val fagsystem: FagsystemDto,
    val saksnummer: String,
    val aktør: String,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalingsperiode: UtbetalingsperiodeDto,
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
    val idRef: UUID? = null,
) {
    companion object
}

data class Opphør(val fom: LocalDate)

