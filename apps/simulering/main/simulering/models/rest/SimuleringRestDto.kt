package simulering.models.rest

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.utsjekk.kontrakter.felles.Ident
import no.nav.utsjekk.kontrakter.felles.Personident
import java.time.LocalDate

object rest {
    data class SimuleringRequest(
        val fagområde: String,
        val fagsystemId: String,
        val personident: Personident,
        val mottaker: Ident,
        val endringskode: Endringskode,
        val saksbehandler: String,
        val utbetalingsfrekvens: Utbetalingsfrekvens,
        val utbetalingslinjer: List<Utbetalingslinje>,
    )
    data class Utbetalingslinje(
        val delytelseId: String,
        val endringskode: Endringskode,
        val klassekode: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val sats: Int,
        val grad: Grad,
        val refDelytelseId: String?,
        val refFagsystemId: String?,
        val datoStatusFom: LocalDate?,
        val statuskode: String?,
        val satstype: SatsType,
        val utbetalesTil: String,
    ) {
        data class Grad(val type: GradType, val prosent: Int?)
        enum class GradType { UFOR } // TODO: finn alle alternativer, eller er det alltidf ufor?
    }

    enum class Endringskode(@get:JsonValue val verdi: String) {
        NY("NY"),
        ENDRING("ENDR");
    }

    enum class Utbetalingsfrekvens(@get:JsonValue val verdi: String) {
        DAGLIG("DAG"),
        UKENTLIG("UKE"),
        HVER_FJORTENDE_DAG("14DG"),
        MÅNEDLIG("MND")
    }

    enum class SatsType(@get:JsonValue val verdi: String) {
        DAG("DAG"),
        MÅNED("MND")
    }

    data class SimuleringResponse(
        val gjelderId: String,
        val gjelderNavn: String,
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
        val fagSystemId: String,
        val utbetalesTilId: String,
        val utbetalesTilNavn: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<Detaljer>,
    )

    data class Detaljer(
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val konto: String,
        val belop: Int,
        val tilbakeforing: Boolean,
        val sats: Double,
        val typeSats: String,
        val antallSats: Int,
        val uforegrad: Int,
        val utbetalingsType: String,
        val klassekode: String,
        val klassekodeBeskrivelse: String?,
        val refunderesOrgNr: String?,
    )
}

