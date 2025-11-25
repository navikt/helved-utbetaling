package utsjekk.utbetaling

import models.badRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
    AAP("AAP"),
    HISTORISK("HELSREF");

    companion object {
        fun from(stønad: Stønadstype): FagsystemDto {
            return FagsystemDto.entries
                .find { it.name == stønad.asFagsystemStr() }
                ?: badRequest("$stønad tilhører et ukjent fagsystem")
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
    val avvent: AvventDto? = null,
) {
    companion object;
}

data class UtbetalingsperiodeDto(
    val erEndringPåEksisterendePeriode: Boolean,
    val id: String,
    val vedtaksdato: LocalDate,
    val klassekode: String,
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

data class AvventDto(
    val fom: LocalDate,
    val tom: LocalDate,
    val overføres: LocalDate,
    val årsak: Årsak? = null,
    val feilregistrering: Boolean = false,
)

data class Opphør(val fom: LocalDate)
