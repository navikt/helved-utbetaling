package urskog

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import models.*
import kotlin.math.abs
import no.nav.system.os.entiteter.beregningskjema.*;
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse

fun intoV1(jaxb: SimulerBeregningResponse): models.Simulering? { 
    return jaxb.response.let { response -> 
        v1.Simulering(
            oppsummeringer = oppsummeringer(response.simulering.beregningsPeriodes),
            detaljer = response.simulering.into(),
        )
    }
}

private fun Beregning.into(): models.v1.SimuleringDetaljer {
    return v1.SimuleringDetaljer(
        gjelderId = this.gjelderId,
        datoBeregnet = LocalDate.parse(this.datoBeregnet),
        totalBeløp = this.belop.toDouble().toInt(),
        perioder = this.beregningsPeriodes.map(::into)
    )
}

private fun into(periode: BeregningsPeriode): models.v1.Periode {
    return v1.Periode(
        fom = LocalDate.parse(periode.periodeFom),
        tom = LocalDate.parse(periode.periodeTom),
        posteringer = periode.beregningStoppnivaas.flatMap { sn ->
            val fagområde = v1.Fagområde.valueOf(sn.kodeFagomraade.trimEnd())
            sn.beregningStoppnivaaDetaljers.map { detaljer ->
                v1.Postering(
                    fagområde = fagområde,
                    sakId = SakId(sn.fagsystemId.trimEnd()), // hva gjør vi med ny/gammel newtypes?
                    fom = LocalDate.parse(detaljer.faktiskFom),
                    tom = LocalDate.parse(detaljer.faktiskTom),
                    beløp = detaljer.belop.toDouble().toInt(),
                    type = requireNotNull(v1.PosteringType.from(detaljer.typeKlasse.trimEnd())) { "models.v1.PosteringsType mangler ${detaljer.typeKlasse}" },
                    klassekode = detaljer.klassekode.trimEnd(),
                )
            }
        }
    )
}

private fun oppsummeringer(beregningsPeriodes: List<BeregningsPeriode>): List<v1.OppsummeringForPeriode> {
    val perioderByYearMonth = beregningsPeriodes.groupBy { 
        YearMonth.parse(it.periodeFom, DateTimeFormatter.ISO_LOCAL_DATE) 
    }

    return perioderByYearMonth.values.map { perioder ->  
        v1.Periode(
            fom = perioder.map { LocalDate.parse(it.periodeFom) }.min(),
            tom = perioder.map { LocalDate.parse(it.periodeTom) }.min(),
            posteringer = perioder.flatMap { periode ->
                periode.beregningStoppnivaas.flatMap { sn ->
                    val fagområde = v1.Fagområde.valueOf(sn.kodeFagomraade.trimEnd())
                    sn.beregningStoppnivaaDetaljers.map { detaljer ->
                        v1.Postering(
                            fagområde = fagområde,
                            sakId = SakId(sn.fagsystemId.trimEnd()), // hva gjør vi med ny/gammel newtypes?
                            fom = LocalDate.parse(detaljer.faktiskFom),
                            tom = LocalDate.parse(detaljer.faktiskTom),
                            beløp = detaljer.belop.toDouble().toInt(),
                            type = requireNotNull(v1.PosteringType.from(detaljer.typeKlasse.trimEnd())) { "models.v1.PosteringsType mangler ${detaljer.typeKlasse}" },
                            klassekode = detaljer.klassekode.trimEnd(),
                        )
                    }
                }
            }
        )
    }
    .map { periode ->
        val totalEtterbetaling = if (periode.fom > LocalDate.now()) 0 else totalEtterbetaling(periode.posteringer) 
        v1.OppsummeringForPeriode(
            fom = periode.fom,
            tom = periode.tom,
            tidligereUtbetalt = tidligereUtbetalt(periode.posteringer),
            nyUtbetaling = nyUtbetaling(periode.posteringer),
            totalEtterbetaling = totalEtterbetaling,
            totalFeilutbetaling = totalFeilutbetaling(periode.posteringer),
        )
    }
}

private fun totalEtterbetaling(posteringer: List<v1.Postering>): Int {
    val justeringer = posteringer.filter { it.type == v1.PosteringType.FEILUTBETALING && it.klassekode in listOf("KL_KODE_JUST_ARBYT", "KL_KODE_JUST_TILLST") }.sumOf { it.beløp }
    val resultat = nyUtbetaling(posteringer) - tidligereUtbetalt(posteringer) 
    return when (justeringer < 0) {
        true  -> maxOf(resultat - abs(justeringer), 0)
        false -> maxOf(resultat, 0)
    }
}

private fun tidligereUtbetalt(posteringer: List<v1.Postering>): Int {
    return abs(posteringer.filter { it.beløp < 0 && it.type == v1.PosteringType.YTELSE }.sumOf { it.beløp })
}

private fun totalFeilutbetaling(posteringer: List<v1.Postering>): Int {
    val positiveFeil = posteringer.filter { it.beløp > 0 && it.type == v1.PosteringType.FEILUTBETALING && it.klassekode in listOf("KL_KODE_FEIL_ARBYT", "KL_KODE_FEIL_TILLST") }.sumOf { it.beløp }
    return maxOf(0, positiveFeil)
}

private fun nyUtbetaling(posteringer: List<v1.Postering>): Int {
    val positiveYtel = posteringer.filter { it.beløp > 0 && it.type == v1.PosteringType.YTELSE }.sumOf { it.beløp }
    val positiveFeil = posteringer.filter { it.beløp > 0 && it.type == v1.PosteringType.FEILUTBETALING && it.klassekode in listOf("KL_KODE_FEIL_ARBYT", "KL_KODE_FEIL_TILLST") }.sumOf { it.beløp }
    return positiveYtel - positiveFeil
}

