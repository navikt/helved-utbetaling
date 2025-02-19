package abetal.models

import abetal.AapTuple
import java.time.LocalDateTime
import java.util.UUID


enum class Action {
    CREATE,
    UPDATE,
    DELETE
}

data class AapUtbetaling(
    val action: Action,
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: StønadTypeAAP,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    val periodetype: Periodetype,
    val perioder: List<Utbetalingsperiode>,
)

fun toDomain(tuple: AapTuple, sakIdWrapper: SakIdWrapper?): Utbetaling {
    return Utbetaling(
        uid = UtbetalingId(UUID.fromString(tuple.uid)),
        action = tuple.aap.action,
        førsteUtbetalingPåSak = sakIdWrapper == null,
        sakId = tuple.aap.sakId,
        behandlingId = tuple.aap.behandlingId,
        lastPeriodeId = PeriodeId(), // FIXME: Denne overskrives når vi utleder oppdragslinjene
        personident = tuple.aap.personident,
        vedtakstidspunkt = tuple.aap.vedtakstidspunkt,
        stønad = tuple.aap.stønad,
        beslutterId = tuple.aap.beslutterId,
        saksbehandlerId = tuple.aap.saksbehandlerId,
        periodetype = tuple.aap.periodetype,
        perioder = tuple.aap.perioder,
    )
}

data class SakIdWrapper(val sakId: String, val uids: Set<UtbetalingId>)
