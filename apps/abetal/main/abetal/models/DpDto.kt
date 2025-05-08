package abetal.models

import abetal.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import models.*

data class DpUtbetaling(
    val dryrun: Boolean = false,
    val behandlingId: String,
    val fagsakId: String,
    val ident: String,
    val vedtakstidspunkt: LocalDateTime,
    val virkningsdato: LocalDate,
    val behandletHendelse: Hendelse,
    val utbetalinger: List<DpUtbetalingsperiode>,
    val stønad: StønadTypeDagpenger,
)

data class Hendelse(
    val datatype: String = "Long",
    val id: String, 
    val type: String = "Meldekort",
)

data class DpUtbetalingsperiode(
    val meldeperiode: String,
    val dato: LocalDate,
    val sats: UInt,
    val utbetaling: UInt,
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = dato,
        tom = dato,
        beløp = utbetaling,
        vedtakssats = sats,
        betalendeEnhet = null,
    )
}

fun toDomain(tuple: DpTuple, sakValue: SakValue?): Utbetaling {
    return Utbetaling(
        dryrun = tuple.dp.dryrun,
        fagsystem = Fagsystem.DAGPENGER,
        uid = UtbetalingId(UUID.fromString(tuple.uid)),
        action = Action.CREATE, // TODO: utled
        førsteUtbetalingPåSak = sakValue?.uids?.isEmpty() ?: true,
        sakId = SakId(tuple.dp.fagsakId),
        behandlingId = BehandlingId(tuple.dp.behandlingId),
        lastPeriodeId = PeriodeId(),
        personident = Personident(tuple.dp.ident),
        vedtakstidspunkt = tuple.dp.vedtakstidspunkt,
        stønad = tuple.dp.stønad,
        beslutterId = Navident("dagpenger"), // FIXME: navnet på systemet
        saksbehandlerId = Navident("dagpenger"), // FIXME: navnet på systemet
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = tuple.dp.utbetalinger.map { it.into() },
    )
}

data class DpTuple(val uid: String, val dp: DpUtbetaling)
