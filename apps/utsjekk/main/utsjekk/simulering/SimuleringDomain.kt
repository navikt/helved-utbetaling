package utsjekk.simulering

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import utsjekk.ApiError.Companion.badRequest
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
)

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

enum class PosteringType(val kode: String) {
    YTELSE("YTEL"),
    FEILUTBETALING("FEIL"),
    FORSKUDSSKATT("SKAT"),
    JUSTERING("JUST"),
    TREKK("TREK"),
    MOTPOSTERING("MOTP"),
    ;

    companion object {
        fun fraKode(kode: String): PosteringType {
            for (type in PosteringType.entries) {
                if (type.kode == kode) {
                    return type
                }
            }
            badRequest("PosteringType finnes ikke for kode $kode")
        }
    }
}

enum class Fagområde(val kode: String) {
    TILLEGGSSTØNADER("TILLST"),
    TILLEGGSSTØNADER_ARENA("TSTARENA"),
    TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING("MTSTAREN"),

    DAGPENGER("DP"),
    DAGPENGER_MANUELL_POSTERING("MDP"),
    DAGPENGER_ARENA("DPARENA"),
    DAGPENGER_ARENA_MANUELL_POSTERING("MDPARENA"),

    TILTAKSPENGER("TILTPENG"),
    TILTAKSPENGER_ARENA("TPARENA"),
    TILTAKSPENGER_ARENA_MANUELL_POSTERING("MTPARENA"),
    ;

    companion object {
        fun fraKode(kode: String): Fagområde {
            for (fagområde in Fagområde.entries) {
                if (fagområde.kode == kode) {
                    return fagområde
                }
            }
            badRequest("Fagområde finnes ikke for kode $kode")
        }
    }
}

fun hentFagområdeKoderFor(fagsystem: Fagsystem): Set<Fagområde> =
    when (fagsystem) {
        Fagsystem.DAGPENGER -> setOf(
            Fagområde.DAGPENGER,
            Fagområde.DAGPENGER_ARENA,
            Fagområde.DAGPENGER_ARENA_MANUELL_POSTERING,
            Fagområde.DAGPENGER_MANUELL_POSTERING,
        )

        Fagsystem.TILTAKSPENGER -> setOf(
            Fagområde.TILTAKSPENGER,
            Fagområde.TILTAKSPENGER_ARENA,
            Fagområde.TILTAKSPENGER_ARENA_MANUELL_POSTERING,
        )

        Fagsystem.TILLEGGSSTØNADER -> setOf(
            Fagområde.TILLEGGSSTØNADER,
            Fagområde.TILLEGGSSTØNADER_ARENA,
            Fagområde.TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING,
        )
    }
