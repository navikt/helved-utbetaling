package urskog

import models.*

import java.time.LocalDate
import kotlin.math.abs
import no.nav.system.os.entiteter.beregningskjema.*;
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse

fun toDomain(jaxb: SimulerBeregningResponse): Simulering {
    return Simulering(
        perioder = jaxb.response.simulering.beregningsPeriodes.map(::into)
    )
}

private fun into(periode: BeregningsPeriode): Simuleringsperiode {
    return Simuleringsperiode(
        fom = LocalDate.parse(periode.periodeFom),
        tom = LocalDate.parse(periode.periodeTom),
        utbetalinger = periode.beregningStoppnivaas.map(::into)
    )
}

private fun into(stoppnivå: BeregningStoppnivaa): SimulertUtbetaling {
    return SimulertUtbetaling(
        fagsystem = Fagsystem.entries.single { it.fagområde == stoppnivå.kodeFagomraade.trimEnd() },
        sakId = stoppnivå.fagsystemId.trimEnd(),
        utbetalesTil =  stoppnivå.utbetalesTilId.removePrefix("00"),
        stønadstype = stoppnivå.stønadstype(),
        tidligereUtbetalt = stoppnivå.tidligereUtbetalt(),
        nyttBeløp = stoppnivå.nyttBeløp(),
    ) 
} 

private fun BeregningStoppnivaa.tidligereUtbetalt(): Int {
    val sumTidligereutbetalinger = beregningStoppnivaaDetaljers
        .filter { it.typeKlasse == Posteringstype.YTEL && it.belop.toInt() < 0 }
        .sumOf { it.belop.toInt() }
    return abs(sumTidligereutbetalinger)
}  

private fun BeregningStoppnivaa.stønadstype(): Stønadstype =
    Stønadstype.fraKode(
        beregningStoppnivaaDetaljers
            .first { it.typeKlasse.trimEnd() == Posteringstype.YTEL }
            .klassekode
            .trimEnd()
    )
    
private fun BeregningStoppnivaa.nyttBeløp(): Int {
    val totalSumYtelser = beregningStoppnivaaDetaljers.ytelser.sum()
    val positiveFeilutbetalinger = beregningStoppnivaaDetaljers.feilutbetalinger.positive.sum()
    return totalSumYtelser - positiveFeilutbetalinger  
}

private val List<BeregningStoppnivaaDetaljer>.feilutbetalinger: List<BeregningStoppnivaaDetaljer>
    get() = filter { it.typeKlasse == Posteringstype.FEIL && it.klassekode.trimEnd() == "KL_KODE_FEIL_ARBYT" }

private val List<BeregningStoppnivaaDetaljer>.positive: List<BeregningStoppnivaaDetaljer>
    get() = filter { it.belop.toInt() > 0 }

private val List<BeregningStoppnivaaDetaljer>.ytelser: List<BeregningStoppnivaaDetaljer>
    get() = filter { it.typeKlasse == Posteringstype.YTEL }

private val List<BeregningStoppnivaaDetaljer>.justeringer: List<BeregningStoppnivaaDetaljer>
    get() = filter { it.typeKlasse == Posteringstype.FEIL && it.klassekode.trimEnd() == "KL_KODE_JUST_ARBYT" }

private fun List<BeregningStoppnivaaDetaljer>.sum(): Int = sumOf { it.belop.toInt() }

object Posteringstype {
    const val YTEL = "YTEL"
    const val FEIL = "FEIL"
    const val SKAT = "SKAT"
    const val JUST = "JUST"
    const val TREK = "TREK"
    const val MOTP = "MOTP" 
}

