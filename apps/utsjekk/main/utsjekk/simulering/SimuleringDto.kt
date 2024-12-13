package utsjekk.simulering

import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.iverksett.ForrigeIverksettingV2Dto
import no.nav.utsjekk.kontrakter.iverksett.UtbetalingV2Dto
import java.time.LocalDate

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
    enum class Fagområde { TILLST, TSTARENA, MTSTAREN, DP, MDP, DPARENA, MDPARENA, TILTPENG, TPARENA, MTPARENA, AAP }

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
    )

    data class OppsummeringForPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val tidligereUtbetalt: Int,
        val nyUtbetaling: Int,
        val totalEtterbetaling: Int,
        val totalFeilutbetaling: Int,
    )
}
