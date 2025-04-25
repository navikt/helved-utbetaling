package abetal.models

import models.*
import java.time.LocalDateTime
import java.util.UUID
import abetal.AapTuple

data class AapUtbetaling(
    val simulate: Boolean,
    val action: Action,
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: StønadTypeAAP,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    val periodetype: Periodetype,
    val avvent: Avvent?,
    val perioder: List<Utbetalingsperiode>,
)

fun toDomain(tuple: AapTuple, sakValue: SakValue?): Utbetaling {
    // TODO: ha med fagsystem?
    return Utbetaling(
        simulate = tuple.aap.simulate,
        uid = UtbetalingId(UUID.fromString(tuple.uid)),
        action = tuple.aap.action,
        førsteUtbetalingPåSak = sakValue?.uids?.isEmpty() ?: true,
        sakId = tuple.aap.sakId,
        behandlingId = tuple.aap.behandlingId,
        lastPeriodeId = PeriodeId(),
        personident = tuple.aap.personident,
        vedtakstidspunkt = tuple.aap.vedtakstidspunkt,
        stønad = tuple.aap.stønad,
        beslutterId = tuple.aap.beslutterId,
        saksbehandlerId = tuple.aap.saksbehandlerId,
        periodetype = tuple.aap.periodetype,
        avvent = tuple.aap.avvent,
        perioder = tuple.aap.perioder,
    )
}

data class SakKey(val sakId: SakId, val fagsystem: Fagsystem)
data class SakValue(val uids: Set<UtbetalingId>)

