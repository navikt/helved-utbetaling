package utsjekk.utbetaling

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import utsjekk.avstemming.nesteVirkedag
import utsjekk.badRequest

data class UtbetalingApi(
    val sakId: String,
    val behandlingId: String,
    val personident: String,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: Stønadstype,
    val beslutterId: String,
    val saksbehandlerId: String,
    val perioder: List<UtbetalingsperiodeApi>,
) {
    companion object {
        fun from(domain: Utbetaling) = UtbetalingApi(
            sakId = domain.sakId.id,
            behandlingId = domain.behandlingId.id,
            personident = domain.personident.ident,
            vedtakstidspunkt = domain.vedtakstidspunkt,
            stønad = domain.stønad,
            beslutterId = domain.beslutterId.ident,
            saksbehandlerId = domain.saksbehandlerId.ident,
            perioder = UtbetalingsperiodeApi.from(domain.periode)
        )
    }

    fun validate() {
        failOnÅrsskifte()
        failOnDuplicatePerioder()
        failOnTomBeforeFom()
        // validate beløp
        // validate fom/tom
        // validate stønadstype opp mot e.g. fastsattdagpengesats
        // validate sakId ikke er for lang
    }
}

data class UtbetalingsperiodeApi(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,

    /**
     * Dette feltet brukes hvis budsjettet til et lokalkontor skal brukes i beregningene.
     */
    val betalendeEnhet: String? = null,

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
                    betalendeEnhet = domain.betalendeEnhet?.enhet,
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
                        betalendeEnhet = domain.betalendeEnhet?.enhet,
                        fastsattDagpengesats = domain.fastsattDagpengesats,
                    )
                    add(periode)
                    date = if (domain.satstype == Satstype.DAG) date.plusDays(1) else date.nesteVirkedag()
                }
            }
        }
    }
}

data class UtbetalingStatusApi(
    val opprettet: LocalDateTime,
    val endret: LocalDateTime,
    val status: Status,
) {
    companion object {
        fun from(domain: UtbetalingStatus): UtbetalingStatusApi = 
            UtbetalingStatusApi(
                opprettet = domain.opprettet,
                endret = domain.endret, 
                status = domain.status,
            ) 
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

private fun UtbetalingApi.failOnDuplicatePerioder() {
    if(perioder.groupBy { it.fom }.any { (_, perioder) -> perioder.size != 1 }) {
        badRequest(
            msg = "kan ikke sende inn duplikate perioder",
            field = "fom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
    if(perioder.groupBy { it.tom }.any { (_, perioder) -> perioder.size != 1 }) {
        badRequest(
            msg = "kan ikke sende inn duplikate perioder",
            field = "tom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
} 

private fun UtbetalingApi.failOnTomBeforeFom() {
    if(!perioder.all { it.fom <= it.tom }) {
        badRequest(
            msg = "fom må være før eller lik tom",
            field = "fom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
}
