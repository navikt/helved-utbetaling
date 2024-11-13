package utsjekk.iverksetting.v3

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import utsjekk.avstemming.nesteVirkedag
import utsjekk.badRequest

data class UtbetalingApi(
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: Stønadstype,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    val perioder: List<UtbetalingsperiodeApi>,
) {
    companion object {
        fun from(domain: Utbetaling) = UtbetalingApi(
            sakId = domain.sakId,
            behandlingId = domain.behandlingId,
            personident = domain.personident,
            vedtakstidspunkt = domain.vedtakstidspunkt,
            stønad = domain.stønad,
            beslutterId = domain.beslutterId,
            saksbehandlerId = domain.saksbehandlerId,
            perioder = UtbetalingsperiodeApi.from(domain.periode)
        )
    }

    fun validate() {
        failOnÅrsskifte()
        // validate beløp
        // validate fom/tom
        // validate stønadstype opp mot e.g. fastsattdagpengesats
    }
}

data class UtbetalingsperiodeApi(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val id: UUID = UUID.randomUUID(),

    /**
     * Dette feltet brukes hvis budsjettet til et lokalkontor skal brukes i beregningene.
     */
    val betalendeEnhet: NavEnhet? = null,

    /**
     * Dagpenger har særegen skatteberegning og må fylle inn dette feltet.
     */
    val fastsattDagpengesats: UInt? = null,
) {
    companion object {
        fun from(domain: Utbetalingsperiode): List<UtbetalingsperiodeApi> = when (domain.satstype) {
            Satstype.ENGANGS, Satstype.MND -> listOf(
                UtbetalingsperiodeApi(
                    fom = domain.fom,
                    tom = domain.tom,
                    beløp = domain.beløp,
                    id = domain.id,
                    betalendeEnhet = domain.betalendeEnhet,
                    fastsattDagpengesats = domain.fastsattDagpengesats,
                )
            )

            Satstype.DAG, Satstype.VIRKEDAG -> buildList {
                var date = domain.fom
                while (date.isBefore(domain.tom) || date.isEqual(domain.tom)) {
                    val periode = UtbetalingsperiodeApi(
                        fom = date,
                        tom = date,
                        beløp = domain.beløp,
                        id = UUID.randomUUID(),
                        betalendeEnhet = domain.betalendeEnhet,
                        fastsattDagpengesats = domain.fastsattDagpengesats,
                    )
                    add(periode)
                    date = if (domain.satstype == Satstype.DAG) date.plusDays(1) else date.nesteVirkedag()
                }
            }
        }
    }
}

private fun UtbetalingApi.failOnÅrsskifte() {
    if (perioder.minBy { it.fom }.fom.year != perioder.maxBy { it.tom }.tom.year) {
        badRequest(
            msg = "periode strekker seg over årsskifte",
            field = "tom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
}

