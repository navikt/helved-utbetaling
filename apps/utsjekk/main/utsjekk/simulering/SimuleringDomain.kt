package utsjekk.simulering

import utsjekk.iverksetting.*
import java.time.LocalDate

data class Simulering(
    val behandlingsinformasjon: Behandlingsinformasjon,
    val nyTilkjentYtelse: TilkjentYtelse,
    val forrigeIverksetting: ForrigeIverksetting?,
) {
    companion object Mapper
}

data class ForrigeIverksetting(
    val behandlingId: BehandlingId,
    val iverksettingId: IverksettingId?,
)

data class SimuleringDetaljer(
    val gjelderId: String,
    val datoBeregnet: LocalDate,
    val totalBeløp: Int,
    val perioder: List<Periode>,
) {
    companion object Mapper
}

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
    val posteringer: List<Postering>,
)

data class Postering(
    val fagområde: Fagområde,
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

    companion object Mapper
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

    HISTORISK,
    ;

    companion object Mapper
}
