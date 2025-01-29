package utsjekk.utbetaling

import utsjekk.badRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"), // TODO: trenger ikke koden i denne appen
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
    AAP("AAP");

    companion object {
        fun from(stønad: Stønadstype): FagsystemDto {
            return FagsystemDto.entries
                .find { it.name == stønad.asFagsystemStr() }
                ?: badRequest("$stønad er ukjent fagsystem")
        }
    }
}

data class UtbetalingsoppdragDto(
    val uid: UtbetalingId,
    val erFørsteUtbetalingPåSak: Boolean,
    val fagsystem: FagsystemDto,
    val saksnummer: String,
    val aktør: String,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalingsperioder: List<UtbetalingsperiodeDto>,
    val avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    val brukersNavKontor: String? = null,
) {
    companion object;
}

data class UtbetalingsperiodeDto(
    val erEndringPåEksisterendePeriode: Boolean,
    val id: String,
    val vedtaksdato: LocalDate,
    val klassekode: String, // TODO: trenger ikke klassekode i denne appen
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: UInt,
    val satstype: Satstype,
    val utbetalesTil: String,
    val behandlingId: String,
    val opphør: Opphør? = null,
    val forrigePeriodeId: String? = null,
    val fastsattDagsats: UInt? = null,
) {
    companion object;
}

data class Opphør(val fom: LocalDate)

