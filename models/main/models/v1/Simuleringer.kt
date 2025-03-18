package models.v1

import java.time.LocalDate
import models.*

data class Simulering(
    val oppsummeringer: List<OppsummeringForPeriode>,
    val detaljer: SimuleringDetaljer,
)
data class OppsummeringForPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val tidligereUtbetalt: Int,
    val nyUtbetaling: Int,
    val totalEtterbetaling: Int,
    val totalFeilutbetaling: Int,
)
data class SimuleringDetaljer(
    val gjelderId: String,
    val datoBeregnet: LocalDate,
    val totalBeløp: Int,
    val perioder: List<Periode>,
) 
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
    val posteringer: List<Postering>,
)
data class Postering(
    val fagområde: Fagområde, // TODO: string?
    val sakId: SakId,
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: Int,
    val type: PosteringType,
    val klassekode: String,
)
enum class PosteringType {
    YTELSE,
    FEILUTBETALING,
    FORSKUDSSKATT,
    JUSTERING,
    TREKK,
    MOTPOSTERING,
    ;
}
enum class Fagområde {
    AAP,

    TILLEGGSSTØNADER,
    TILLEGGSSTØNADER_ARENA,
    TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING,

    DAGPENGER,
    DAGPENGER_MANUELL_POSTERING,
    DAGPENGER_ARENA,
    DAGPENGER_ARENA_MANUELL_POSTERING,

    TILTAKSPENGER,
    TILTAKSPENGER_ARENA,
    TILTAKSPENGER_ARENA_MANUELL_POSTERING,
    ;
}
