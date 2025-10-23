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
    val fagområde: Fagområde,
    val sakId: SakId,
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: Int,
    val type: PosteringType,
    val klassekode: String,
)

enum class PosteringType(val typeKlasse: String) {
    YTELSE("YTEL"),
    FEILUTBETALING("FEIL"),
    FORSKUDSSKATT("SKAT"),
    JUSTERING("JUST"),
    TREKK("TREK"),
    MOTPOSTERING("MOTP"),
;

    companion object {
        fun from(typeKlasse: String) = entries.singleOrNull { it.typeKlasse == typeKlasse } 
    }
}

enum class Fagområde(val fagsystem: Fagsystem) { 
    TILLSTPB(Fagsystem.TILLEGGSSTØNADER),
    TILLSTLM(Fagsystem.TILLEGGSSTØNADER), 
    TILLSTBO(Fagsystem.TILLEGGSSTØNADER), 
    TILLSTDR(Fagsystem.TILLEGGSSTØNADER), 
    TILLSTRS(Fagsystem.TILLEGGSSTØNADER), 
    TILLSTRO(Fagsystem.TILLEGGSSTØNADER), 
    TILLSTRA(Fagsystem.TILLEGGSSTØNADER), 
    TILLSTFL(Fagsystem.TILLEGGSSTØNADER), 
    TILLST(Fagsystem.TILLEGGSSTØNADER),   
    TSTARENA(Fagsystem.TILLEGGSSTØNADER),
    MTSTAREN(Fagsystem.TILLEGGSSTØNADER),
    DP(Fagsystem.DAGPENGER),
    MDP(Fagsystem.DAGPENGER),
    DPARENA(Fagsystem.DAGPENGER),
    MDPARENA(Fagsystem.DAGPENGER),
    TILTPENG(Fagsystem.TILTAKSPENGER),
    TPARENA(Fagsystem.TILTAKSPENGER),
    MTPARENA(Fagsystem.TILTAKSPENGER),
    AAP(Fagsystem.AAP),
    HELSREF(Fagsystem.HISTORISK),
}

