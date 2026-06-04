package simulering.models.rest

import kotlinx.serialization.Serializable
import simulering.LocalDateSerializer
import simulering.LocalDateTimeSerializer
import simulering.UtbetalingIdSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
@JvmInline
value class UtbetalingId(@Serializable(with = simulering.UUIDSerializer::class) val id: UUID) {
    companion object
}

@Serializable
enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
    AAP("AAP"),
    HISTORISK("HELSREF");
}

@Serializable
data class UtbetalingsoppdragDto(
    val uid: UtbetalingId,
    val erFørsteUtbetalingPåSak: Boolean,
    val fagsystem: FagsystemDto,
    val saksnummer: String,
    val aktør: String,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalingsperioder: List<UtbetalingsperiodeDto>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    val brukersNavKontor: String? = null,
) {
    companion object;
}

@Serializable
data class UtbetalingsperiodeDto(
    val erEndringPåEksisterendePeriode: Boolean,
    val id: String,
    @Serializable(with = LocalDateSerializer::class)
    val vedtaksdato: LocalDate,
    val klassekode: String,
    @Serializable(with = LocalDateSerializer::class)
    val fom: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val tom: LocalDate,
    val sats: UInt,
    val satstype: Satstype,
    val utbetalesTil: String,
    val behandlingId: String,
    val opphør: Opphør? = null,
    val forrigePeriodeId: String? = null,
) {
    companion object;
}

@Serializable
data class Opphør(
    @Serializable(with = LocalDateSerializer::class)
    val fom: LocalDate,
)

@Serializable
enum class Satstype(val value: String) {
    DAG("DAG7"),
    VIRKEDAG("DAG"),
    MND("MND"),
    ENGANGS("ENG");
}
