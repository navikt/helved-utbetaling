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
    TILLEGGSSTØNADER("TILLST");
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
    val id: UInt,
    val vedtaksdato: LocalDate,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: UInt,
    val satstype: Satstype,
    val utbetalesTil: String,
    val behandlingId: String,
    val opphør: Opphør? = null,
    val forrigePeriodeId: UInt? = null,
    val fastsattDagsats: UInt? = null,
) {
    companion object;
}

data class Opphør(val fom: LocalDate)

enum class Satstype(val value: String) {
    DAG("DAG7"),
    VIRKEDAG("DAG"),
    MND("MND"),
    ENGANGS("ENG");
}
