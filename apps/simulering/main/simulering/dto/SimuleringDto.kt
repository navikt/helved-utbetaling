package simulering.dto

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.utsjekk.kontrakter.felles.Ident
import no.nav.utsjekk.kontrakter.felles.Personident
import java.time.LocalDate

data class SimuleringApiDto(
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
    val satstype: Satstype,
    val utbetalesTil: String,
)

data class Grad(val type: GradType, val prosent: Int?) // TODO: i tjenestekontrakt står det int maks 100. Er dette %?
enum class GradType { UFOR } // TODO: finn alle alternativer, eller er det alltidf ufor?

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

enum class Satstype(@get:JsonValue val verdi: String) {
    DAG("DAG"),
    MÅNED("MND")
}