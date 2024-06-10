package simulering.models.rest

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.utsjekk.kontrakter.felles.Personident
import java.time.LocalDate

object rest {
    data class SimuleringRequest(
        val fagområde: String,
        val sakId: String,
        val personident: Personident,
        val erFørsteUtbetalingPåSak: Boolean,
        val saksbehandler: String,
        val utbetalingsperioder: List<Utbetalingsperiode>,
    )
    data class Utbetalingsperiode(
        val periodeId: String,
        val forrigePeriodeId: String?,
        val erEndringPåEksisterendePeriode: Boolean,
        val klassekode: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val sats: Int,
        val satstype: SatsType,
        val opphør: Opphør?,
        val utbetalesTil: String,
    )

    data class Opphør(val fom: LocalDate)

    enum class SatsType(@get:JsonValue val verdi: String) {
        DAG("DAG"),
        MÅNED("MND"),
        ENGANGS("ENG"),
    }

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

    data class Utbetaling(
        val fagområde: String,
        val fagSystemId: String,
        val utbetalesTilId: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<Postering>,
    )

    // Tilsvarer én rad i regnskapet
    data class Postering(
        val type: String,
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val belop: Int,
        val sats: Double,
        val satstype: String,
        val klassekode: String,
        val trekkVedtakId: Long?,
        val refunderesOrgNr: String?,
    )
}

