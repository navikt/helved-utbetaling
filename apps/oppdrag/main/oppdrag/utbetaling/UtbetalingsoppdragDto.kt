package oppdrag.utbetaling

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@JvmInline
value class UtbetalingId(val id: UUID) {
    companion object
}

enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
    AAP("AAP");
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

enum class Årsak(val kode: String) {
    AVVENT_AVREGNING("AVAV"),
    AVVENT_REFUSJONSKRAV("AVRK"),
}

data class AvventDto(
    val fom: LocalDate,
    val tom: LocalDate,
    val overføres: LocalDate,
    val årsak: Årsak? = null,
    val feilregistrering: Boolean = false,
)

data class Opphør(val fom: LocalDate)

enum class Satstype(val value: String) {
    DAG("DAG7"),
    VIRKEDAG("DAG"),
    MND("MND"),
    ENGANGS("ENG");
}
