package models

import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse
import java.time.LocalDate
import kotlin.math.abs

data class Simulering(
    val perioder: List<Simuleringsperiode>,
) {
    companion object {
        fun from(dto: SimulerBeregningResponse) = Simulering(
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

private fun BeregningStoppnivaa.tidligereUtbetalt(): Int {
    val sumTidligereutbetalinger = beregningStoppnivaaDetaljers
        .filter { Type.YTEL == Type.from(it.typeKlasse) && it.belop.toInt() < 0 }
        .sumOf { it.belop.toInt() }
    return abs(sumTidligereutbetalinger)
}  

private fun BeregningStoppnivaa.stønadstype(): Stønadstype? =
    beregningStoppnivaaDetaljers
        .firstOrNull { Type.YTEL == Type.from(it.typeKlasse) }
        ?.klassekode
        ?.trimEnd()
        ?.let(Stønadstype::fraKode)

private fun BeregningStoppnivaa.nyttBeløp(): Int {
    val totalSumYtelser = beregningStoppnivaaDetaljers.ytelser.sum()
    val positiveFeilutbetalinger = beregningStoppnivaaDetaljers.feilutbetalinger.positive.sum()
    return totalSumYtelser - positiveFeilutbetalinger  
}

private val List<BeregningStoppnivaaDetaljer>.ytelser: List<BeregningStoppnivaaDetaljer>
    get() = filter { Type.YTEL == Type.from(it.typeKlasse) }

private val List<BeregningStoppnivaaDetaljer>.feilutbetalinger: List<BeregningStoppnivaaDetaljer>
    get() = filter { Type.FEIL == Type.from(it.typeKlasse) && it.klassekode.trimEnd() == "KL_KODE_FEIL_ARBYT" }

private val List<BeregningStoppnivaaDetaljer>.positive: List<BeregningStoppnivaaDetaljer>
    get() = filter { it.belop.toInt() > 0 }

private val List<BeregningStoppnivaaDetaljer>.justeringer: List<BeregningStoppnivaaDetaljer>
    get() = filter { Type.FEIL == Type.from(it.typeKlasse) && it.klassekode.trimEnd() == "KL_KODE_JUST_ARBYT" }

private fun List<BeregningStoppnivaaDetaljer>.sum(): Int = sumOf { it.belop.toInt() }
