package models

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse
import java.time.LocalDate
import kotlin.math.abs

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(models.v1.Simulering::class, name = "v1"),
    JsonSubTypes.Type(models.v2.Simulering::class, name = "v2"),
    JsonSubTypes.Type(Info::class, name = "info"),
)
sealed interface Simulering

object v1 {
    data class Simulering(
        val oppsummeringer: List<OppsummeringForPeriode>,
        val detaljer: SimuleringDetaljer,
    ): models.Simulering

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
}

object v2 {
    data class Simulering(
        val perioder: List<Simuleringsperiode>,
    ): models.Simulering {
        companion object {
            fun from(dto: SimulerBeregningResponse): models.Simulering = Simulering(
                perioder = when (dto.response) {
                    // Hvis responsen er tom er det snakk om en simulering av et opphør av noe som ikke har blitt effektuert av
                    // oppdragssystemet enda.
                    null -> emptyList()
                    else -> dto.response.simulering.beregningsPeriodes.map(Simuleringsperiode::from)
                }
            )
        }
    }

    data class Simuleringsperiode(
        val fom: LocalDate,
        val tom: LocalDate,
        val utbetalinger: List<SimulertUtbetaling>,
    ) {
        companion object {
            fun from(dto: BeregningsPeriode) = Simuleringsperiode(
                fom = LocalDate.parse(dto.periodeFom.trimEnd()),
                tom = LocalDate.parse(dto.periodeTom.trimEnd()),
                utbetalinger = dto.beregningStoppnivaas.map(SimulertUtbetaling::from),
            )
        }
    }

    data class SimulertUtbetaling(
        val fagsystem: Fagsystem,
        val sakId: String,
        val utbetalesTil: String,
        val stønadstype: Stønadstype?,
        val tidligereUtbetalt: Int,
        val nyttBeløp: Int,
        val posteringer: List<Postering>,
    ) {
        companion object {
            fun from(dto: BeregningStoppnivaa) = SimulertUtbetaling(
                fagsystem = Fagsystem.entries.single { it.fagområde == dto.kodeFagomraade.trimEnd() },
                sakId = dto.fagsystemId.trimEnd(),
                utbetalesTil = dto.utbetalesTilId.removePrefix("00").trimEnd(),
                stønadstype = dto.stønadstype(),
                tidligereUtbetalt = dto.tidligereUtbetalt(),
                nyttBeløp = dto.nyttBeløp(),
                posteringer = dto.beregningStoppnivaaDetaljers.map(Postering::from),
            )
        }
    }

    data class Postering(
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: Int,
        val type: Type,
        val klassekode: String,
    ) {
        companion object {
            fun from(dto: BeregningStoppnivaaDetaljer) = Postering(
                fom = LocalDate.parse(dto.faktiskFom.trimEnd()),
                tom = LocalDate.parse(dto.faktiskTom.trimEnd()),
                beløp = dto.belop.toInt(),
                type = Type.valueOf(dto.typeKlasse.trimEnd()),
                klassekode = dto.klassekode.trimEnd(),
            )
        }
    }

    enum class Type(val typeKlasse: String) {
        YTEL("YTEL"),
        FEIL("FEIL"),
        SKAT("SKAT"),
        JUST("JUST"),
        TREK("TREK"),
        MOTP("MOTP");

        companion object {
            fun from(typeKlasse: String) = Type.valueOf(typeKlasse.trimEnd())
        }
    }
}

private fun BeregningStoppnivaa.tidligereUtbetalt(): Int {
    val sumTidligereutbetalinger = beregningStoppnivaaDetaljers
        .filter { v2.Type.YTEL == v2.Type.from(it.typeKlasse) && it.belop.toInt() < 0 }
        .sumOf { it.belop.toInt() }
    return abs(sumTidligereutbetalinger)
}  

private fun BeregningStoppnivaa.stønadstype(): Stønadstype? =
    beregningStoppnivaaDetaljers
        .firstOrNull { v2.Type.YTEL == v2.Type.from(it.typeKlasse) }
        ?.klassekode
        ?.trimEnd()
        ?.let(Stønadstype::fraKode)

private fun BeregningStoppnivaa.nyttBeløp(): Int {
    val totalSumYtelser = beregningStoppnivaaDetaljers.ytelser.sum()
    val positiveFeilutbetalinger = beregningStoppnivaaDetaljers.feilutbetalinger.positive.sum()
    return totalSumYtelser - positiveFeilutbetalinger  
}

private val List<BeregningStoppnivaaDetaljer>.ytelser: List<BeregningStoppnivaaDetaljer>
    get() = filter { v2.Type.YTEL == v2.Type.from(it.typeKlasse) }

private val List<BeregningStoppnivaaDetaljer>.feilutbetalinger: List<BeregningStoppnivaaDetaljer>
    get() = filter { v2.Type.FEIL == v2.Type.from(it.typeKlasse) && it.klassekode.trimEnd() == "KL_KODE_FEIL_ARBYT" }

private val List<BeregningStoppnivaaDetaljer>.positive: List<BeregningStoppnivaaDetaljer>
    get() = filter { it.belop.toInt() > 0 }

private val List<BeregningStoppnivaaDetaljer>.justeringer: List<BeregningStoppnivaaDetaljer>
    get() = filter { v2.Type.FEIL == v2.Type.from(it.typeKlasse) && it.klassekode.trimEnd() == "KL_KODE_JUST_ARBYT" }

private fun List<BeregningStoppnivaaDetaljer>.sum(): Int = sumOf { it.belop.toInt() }

data class Info (
    val status: Status,
    val fagsystem: Fagsystem,
    val message: String,
): Simulering {
    enum class Status {
        OK_UTEN_ENDRING,
    }

    companion object {
        fun OkUtenEndring(fagsystem: Fagsystem): Simulering {
            return Info(
                Status.OK_UTEN_ENDRING,
                fagsystem,
                "Simuleringen er helt lik som det forrige oppdraget. Det er ingen endring",
            )
        }
    }
}

