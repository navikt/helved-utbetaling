package utsjekk.simulering.oppsummering

import utsjekk.simulering.Postering
import utsjekk.simulering.PosteringType
import utsjekk.simulering.SimuleringDetaljer
import utsjekk.simulering.api
import java.time.LocalDate
import kotlin.math.abs

object OppsummeringGenerator {
    fun lagOppsummering(detaljer: SimuleringDetaljer): api.SimuleringRespons {
        val oppsummeringer =
            detaljer.perioder.map {
                api.OppsummeringForPeriode(
                    fom = it.fom,
                    tom = it.tom,
                    tidligereUtbetalt = beregnTidligereUtbetalt(it.posteringer),
                    nyUtbetaling = beregnNyUtbetaling(it.posteringer),
                    totalEtterbetaling = if (it.fom > LocalDate.now()) 0 else beregnEtterbetaling(it.posteringer),
                    totalFeilutbetaling = beregnFeilutbetaling(it.posteringer),
                )
            }
        return api.SimuleringRespons(oppsummeringer = oppsummeringer, detaljer = detaljer)
    }

    private fun beregnTidligereUtbetalt(posteringer: List<Postering>): Int =
        abs(posteringer.summerBareNegativePosteringer(PosteringType.YTELSE))

    private fun beregnNyUtbetaling(posteringer: List<Postering>): Int =
        posteringer.summerBarePositivePosteringer(PosteringType.YTELSE) -
                posteringer.summerBarePositivePosteringer(PosteringType.FEILUTBETALING)

    /**
     * Hvis perioden har en positiv feilutbetaling, kan det per def ikke være noen etterbetaling (det overskytende beløpet ville i så fall kansellert ut feilutbetalingen)
     * Hvis perioden har en negativ feilutbetaling, betyr det at man øker ytelsen i en periode det er registrert feilutbetaling på tidligere og tilbakekrevingsbehandlingen ikke er avsluttet.
     * Ved iverksetting av vedtaket ville feilutbetalingen i OS blitt redusert tilsvarende beløpet på posteringen for den negative feilutbetalingen.
     */
    private fun beregnEtterbetaling(posteringer: List<Postering>): Int =
        if (detFinnesPositivFeilutbetaling(posteringer)) {
            0
        } else {
            maxOf(
                0,
                beregnResultat(posteringer) - abs(posteringer.summerBareNegativePosteringer(PosteringType.FEILUTBETALING)),
            )
        }

    private fun beregnFeilutbetaling(posteringer: List<Postering>): Int =
        posteringer.summerBarePositivePosteringer(PosteringType.FEILUTBETALING)

    private fun beregnResultat(posteringer: List<Postering>): Int =
        if (detFinnesPositivFeilutbetaling(posteringer)) {
            -posteringer.summerBarePositivePosteringer(PosteringType.FEILUTBETALING)
        } else {
            beregnNyUtbetaling(posteringer) - beregnTidligereUtbetalt(posteringer)
        }

    private fun detFinnesPositivFeilutbetaling(posteringer: List<Postering>): Boolean =
        posteringer.any { it.type == PosteringType.FEILUTBETALING && it.beløp > 0 }

    private fun List<Postering>.summerBarePositivePosteringer(type: PosteringType): Int =
        this.filter { it.beløp > 0 && it.type == type }.sumOf { it.beløp }

    private fun List<Postering>.summerBareNegativePosteringer(type: PosteringType): Int =
        this.filter { it.beløp < 0 && it.type == type }.sumOf { it.beløp }
}