@file:UseSerializers(models.kotlinx.LocalDateSerializer::class)

package simulering.models.rest

import kotlinx.serialization.Serializable
import models.kontrakter.Personident
import kotlinx.serialization.UseSerializers
import simulering.PersonidentSerializer
import java.time.LocalDate

object rest {

    @Serializable
    data class SimuleringRequest(
        val fagområde: String,
        val sakId: String,
        @Serializable(with = PersonidentSerializer::class)
        val personident: Personident,
        val erFørsteUtbetalingPåSak: Boolean,
        val saksbehandler: String,
        val utbetalingsperioder: List<Utbetalingsperiode>,
    )

    @Serializable
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

    @Serializable
    data class Opphør(
        val fom: LocalDate,
    )

    @Serializable
    enum class SatsType(val verdi: String) {
        DAG("DAG"),
        DAG_INKL_HELG("DAG7"),
        MÅNED("MND"),
        ENGANGS("ENG"),
    }

    @Serializable
    data class SimuleringResponse(
        val gjelderId: String,
        val datoBeregnet: LocalDate,
        val totalBelop: Int,
        val perioder: List<SimulertPeriode>,
    )

    @Serializable
    data class SimulertPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val utbetalinger: List<Utbetaling>,
    )

    @Serializable
    data class Utbetaling(
        val fagområde: String,
        val fagSystemId: String,
        val utbetalesTilId: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<Postering>,
    )

    // Tilsvarer én rad i regnskapet
    @Serializable
    data class Postering(
        val type: String,
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val belop: Int,
        val sats: Double,
        val satstype: String?,
        val klassekode: String,
        val trekkVedtakId: Long?,
        val refunderesOrgNr: String?,
    )
}
