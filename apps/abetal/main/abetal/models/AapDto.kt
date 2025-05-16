package abetal.models

import abetal.*
import java.time.LocalDateTime
import java.util.UUID
import models.*

data class AapUtbetaling(
    val dryrun: Boolean,
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
    return Utbetaling(
        dryrun = tuple.aap.dryrun,
        originalKey = tuple.uid,
        fagsystem = Fagsystem.AAP,
        uid = UtbetalingId(UUID.fromString(tuple.uid)),
        action = tuple.aap.action,
        førsteUtbetalingPåSak = sakValue?.uids?.isEmpty() ?: true,
        utbetalingerPåSak = sakValue?.uids ?: emptySet(), 
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

data class AapTuple(val uid: String, val aap: AapUtbetaling)

