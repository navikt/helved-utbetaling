package utsjekk.simulering

import models.kontrakter.felles.Personident
import models.kontrakter.iverksett.ForrigeIverksettingV2Dto
import models.kontrakter.iverksett.UtbetalingV2Dto
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

object client {
    data class SimuleringResponse(
        val gjelderId: String,
        val datoBeregnet: LocalDate,
        val totalBelop: Int,
        val perioder: List<SimulertPeriode>,
    )

    data class SimulertPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val utbetalinger: List<Utbetaling>,
    )

    // Dette er det samme som et stoppnivå i SOAP
    data class Utbetaling(
        val fagområde: Fagområde,
        val fagSystemId: String,
        val utbetalesTilId: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<PosteringDto>,
    )

    // Tilsvarer én rad i regnskapet
    data class PosteringDto(
        val type: PosteringType,
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val belop: Int,
        val sats: Double,
        val satstype: String?,
        val klassekode: String,
        val trekkVedtakId: Long?,
        val refunderesOrgNr: String?,
    )

    enum class Satstype { DAG, DAG7, MND, ENG }
    enum class PosteringType { YTEL, FEIL, SKAT, JUST, TREK, MOTP }
    enum class Fagområde { TILLST, TSTARENA, MTSTAREN, DP, MDP, DPARENA, MDPARENA, TILTPENG, TPARENA, MTPARENA, AAP, HELSREF }

    data class SimuleringRequest(
        val fagområde: Fagområde,
        val sakId: String,
        val personident: Personident,
        val erFørsteUtbetalingPåSak: Boolean,
        val saksbehandler: String,
        val utbetalingsperioder: List<Utbetalingsperiode>,
    ) {
        companion object Mapper
    }

    data class Utbetalingsperiode(
        val periodeId: String,
        val forrigePeriodeId: String?,
        val erEndringPåEksisterendePeriode: Boolean,
        val klassekode: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val sats: Int,
        val satstype: Satstype,
        val opphør: Opphør?,
        val utbetalesTil: String,
        val fastsattDagsats: BigDecimal?,
    ) {
        companion object Mapper
    }

    data class Opphør(val fom: LocalDate)
}

object api {
    data class SimuleringRequest(
        val sakId: String,
        val behandlingId: String,
        val personident: Personident,
        val saksbehandlerId: String,
        val utbetalinger: List<UtbetalingV2Dto>,
        val forrigeIverksetting: ForrigeIverksettingV2Dto? = null,
    )

    data class SimuleringRespons(
        val oppsummeringer: List<OppsummeringForPeriode>,
        val detaljer: SimuleringDetaljer,
    ) {
        companion object {

            /**
             * Se dokumentasjon: https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/simulering.md
             */
            fun from(detaljer: SimuleringDetaljer): SimuleringRespons {
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

        }
    }

    data class OppsummeringForPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val tidligereUtbetalt: Int,
        val nyUtbetaling: Int,
        val totalEtterbetaling: Int,
        val totalFeilutbetaling: Int,
    )
}

private fun beregnTidligereUtbetalt(posteringer: List<Postering>): Int =
    abs(posteringer.summerBareNegativePosteringer(PosteringType.YTELSE))

private fun beregnNyttBeløp(posteringer: List<Postering>): Int =
    posteringer.summerBarePositivePosteringer(PosteringType.YTELSE) - posteringer.summerBarePositivePosteringer(PosteringType.FEILUTBETALING, KLASSEKODE_FEILUTBETALING)

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

