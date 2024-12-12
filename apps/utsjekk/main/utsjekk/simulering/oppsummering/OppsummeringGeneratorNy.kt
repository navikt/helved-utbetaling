package utsjekk.simulering.oppsummering

import utsjekk.simulering.Periode
import utsjekk.simulering.Postering
import utsjekk.simulering.PosteringType
import utsjekk.simulering.SimuleringDetaljer
import utsjekk.simulering.api
import java.time.LocalDate
import kotlin.math.abs

object OppsummeringGeneratorNy {
    fun lagOppsummering(detaljer: SimuleringDetaljer): api.SimuleringRespons {
        val oppsummeringer =
            detaljer.perioder.slåSammenInnenforSammeMåned().map {
                api.OppsummeringForPeriode(
                    fom = it.fom,
                    tom = it.tom,
                    tidligereUtbetalt = beregnTidligereUtbetalt(it.posteringer),
                    nyUtbetaling = beregnNyttBeløp(it.posteringer),
                    totalEtterbetaling = if (it.fom > LocalDate.now()) 0 else beregnEtterbetaling(it.posteringer),
                    totalFeilutbetaling = beregnFeilutbetaling(it.posteringer),
                )
            }
        return api.SimuleringRespons(oppsummeringer = oppsummeringer, detaljer = detaljer)
    }

    private fun List<Periode>.slåSammenInnenforSammeMåned(): List<Periode> {
        val måneder = this.groupBy { it.fom.month }
        return måneder.values.map { perioder ->
            Periode(
                perioder.minBy { it.fom }.fom,
                perioder.maxBy { it.tom }.tom,
                perioder.flatMap { it.posteringer })
        }
    }

    private fun beregnTidligereUtbetalt(posteringer: List<Postering>): Int =
        abs(posteringer.summerBareNegativePosteringer(PosteringType.YTELSE))

    private fun beregnNyttBeløp(posteringer: List<Postering>): Int =
        posteringer.summerBarePositivePosteringer(PosteringType.YTELSE) - posteringer.summerBarePositivePosteringer(PosteringType.FEILUTBETALING, KLASSEKODE_FEILUTBETALING)

    /**
     * Hvis perioden har en positiv feilutbetaling, kan det per def ikke være noen etterbetaling (det overskytende beløpet ville i så fall kansellert ut feilutbetalingen)
     * Hvis perioden har en negativ feilutbetaling, betyr det at man øker ytelsen i en periode det er registrert feilutbetaling på tidligere og tilbakekrevingsbehandlingen ikke er avsluttet.
     * Ved iverksetting av vedtaket ville feilutbetalingen i OS blitt redusert tilsvarende beløpet på posteringen for den negative feilutbetalingen.
     */
    private fun beregnEtterbetaling(posteringer: List<Postering>): Int {
        val justeringer = posteringer.summerPosteringer(PosteringType.FEILUTBETALING, KLASSEKODE_JUSTERING)
        val resultat = beregnNyttBeløp(posteringer) - beregnTidligereUtbetalt(posteringer)
        return if (justeringer < 0) {
            maxOf(resultat - abs(justeringer), 0)
        } else {
            maxOf(resultat, 0)
        }
    }

    private fun beregnFeilutbetaling(posteringer: List<Postering>): Int =
        maxOf(0, posteringer.summerBarePositivePosteringer(PosteringType.FEILUTBETALING, KLASSEKODE_FEILUTBETALING))

    private fun List<Postering>.summerBarePositivePosteringer(type: PosteringType): Int =
        this.filter { it.beløp > 0 && it.type == type }.sumOf { it.beløp }

    private fun List<Postering>.summerBareNegativePosteringer(type: PosteringType): Int =
        this.filter { it.beløp < 0 && it.type == type }.sumOf { it.beløp }

    private fun List<Postering>.summerBarePositivePosteringer(type: PosteringType, klassekode: String): Int =
        this.filter { it.beløp > 0 && it.type == type && it.klassekode == klassekode }.sumOf { it.beløp }

    private fun List<Postering>.summerPosteringer(type: PosteringType, klassekode: String): Int =
        this.filter { it.type == type && it.klassekode == klassekode }.sumOf { it.beløp }

    const val KLASSEKODE_JUSTERING = "KL_KODE_JUST_ARBYT"
    const val KLASSEKODE_FEILUTBETALING = "KL_KODE_FEIL_ARBYT"
}