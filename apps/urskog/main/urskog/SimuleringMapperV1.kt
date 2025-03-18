package urskog

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import no.nav.system.os.entiteter.beregningskjema.*;
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse

fun intoV1(jaxb: SimulerBeregningResponse) = models.v1.Simulering(
    oppsummeringer = oppsummeringer(jaxb.response.simulering.beregningsPeriodes),
    detaljer = jaxb.response.simulering.into(),
)

private fun Beregning.into(): models.v1.SimuleringDetaljer {
    return models.v1.SimuleringDetaljer(
        gjelderId = this.gjelderId,
        datoBeregnet = LocalDate.parse(this.datoBeregnet),
        totalBeløp = this.belop.toDouble().toInt(),
        perioder = this.beregningsPeriodes.map(::into)
    )
}

private fun into(periode: BeregningsPeriode): models.v1.Periode {
    return models.v1.Periode(
        fom = LocalDate.parse(periode.periodeFom),
        tom = LocalDate.parse(periode.periodeTom),
        posteringer = periode.beregningStoppnivaas.flatMap { sn ->
            val fagområde = models.v1.Fagområde.valueOf(sn.kodeFagomraade.trimEnd())
            sn.beregningStoppnivaaDetaljers.map { detaljer ->
                models.v1.Postering(
                    fagområde = fagområde,
                    sakId = models.SakId(sn.fagsystemId.trimEnd()), // hva gjør vi med ny/gammel newtypes?
                    fom = LocalDate.parse(detaljer.faktiskFom),
                    tom = LocalDate.parse(detaljer.faktiskTom),
                    beløp = detaljer.belop.toDouble().toInt(),
                    type = models.v1.PosteringType.valueOf(detaljer.typeKlasse),
                        klassekode = detaljer.klassekode.trimEnd(),
                    )
                }
            }
        )
}

private fun oppsummeringer(beregningsPeriodes: List<BeregningsPeriode>): List<models.v1.OppsummeringForPeriode> {
    val perioderByYearMonth = beregningsPeriodes.groupBy { 
        YearMonth.parse(it.periodeFom, DateTimeFormatter.ISO_LOCAL_DATE) 
    }

    return perioderByYearMonth.values.map { perioder ->  
        models.v1.Periode(
            fom = perioder.map { LocalDate.parse(it.periodeFom) }.min(),
            tom = perioder.map { LocalDate.parse(it.periodeTom) }.min(),
            posteringer = perioder.flatMap { periode ->
                periode.beregningStoppnivaas.flatMap { sn ->
                    val fagområde = models.v1.Fagområde.valueOf(sn.kodeFagomraade.trimEnd())
                    sn.beregningStoppnivaaDetaljers.map { detaljer ->
                        models.v1.Postering(
                            fagområde = fagområde,
                            sakId = models.SakId(sn.fagsystemId.trimEnd()), // hva gjør vi med ny/gammel newtypes?
                            fom = LocalDate.parse(detaljer.faktiskFom),
                            tom = LocalDate.parse(detaljer.faktiskTom),
                            beløp = detaljer.belop.toDouble().toInt(),
                            type = models.v1.PosteringType.valueOf(detaljer.typeKlasse),
                            klassekode = detaljer.klassekode.trimEnd(),
                        )
                    }
                }
            }
        )
    }
    .map { periode ->
        val totalEtterbetaling = if (periode.fom > LocalDate.now()) 0 else totalEtterbetaling(periode.posteringer) 
        models.v1.OppsummeringForPeriode(
            fom = periode.fom,
            tom = periode.tom,
            tidligereUtbetalt = tidligereUtbetalt(periode.posteringer),
            nyUtbetaling = nyUtbetaling(periode.posteringer),
            totalEtterbetaling = totalEtterbetaling,
            totalFeilutbetaling = totalFeilutbetaling(periode.posteringer),
        )
    }
}

private fun totalEtterbetaling(posteringer: List<models.v1.Postering>): Int {
    val justeringer = posteringer.filter { it.type == models.v1.PosteringType.FEILUTBETALING && it.klassekode == "KL_KODE_JUST_ARBYT" }.sumOf { it.beløp }
    val resultat = nyUtbetaling(posteringer) - tidligereUtbetalt(posteringer) 
    return when (justeringer < 0) {
        true  -> maxOf(resultat - abs(justeringer), 0)
        false -> maxOf(resultat, 0)
    }
}

private fun tidligereUtbetalt(posteringer: List<models.v1.Postering>): Int {
    return abs(posteringer.filter { it.beløp < 0 && it.type == models.v1.PosteringType.YTELSE }.sumOf { it.beløp })
}

private fun totalFeilutbetaling(posteringer: List<models.v1.Postering>): Int {
    val positiveFeil = posteringer.filter { it.beløp > 0 && it.type == models.v1.PosteringType.FEILUTBETALING && it.klassekode == "KL_KODE_FEIL_ARBYT" }.sumOf { it.beløp }
    return maxOf(0, positiveFeil)
}

private fun nyUtbetaling(posteringer: List<models.v1.Postering>): Int {
    val positiveYtel = posteringer.filter { it.beløp > 0 && it.type == models.v1.PosteringType.YTELSE }.sumOf { it.beløp }
    val positiveFeil = posteringer.filter { it.beløp > 0 && it.type == models.v1.PosteringType.FEILUTBETALING && it.klassekode == "KL_KODE_FEIL_ARBYT" }.sumOf { it.beløp }
    return positiveYtel - positiveFeil
}

