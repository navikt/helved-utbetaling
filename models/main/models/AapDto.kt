package models

import java.time.LocalDate
import java.time.LocalDateTime

data class AapUtbetaling(
    val dryrun: Boolean = false,

    val sakId: String,
    val behandlingId: String,
    val ident: String,
    val utbetalinger: List<AapUtbetalingsdag>,
    val vedtakstidspunktet: LocalDateTime,
)

data class AapUtbetalingsdag(
    val meldeperiode: String,
    val dato: LocalDate,
    val sats: UInt,
    val utbetaltBeløp: UInt,
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = dato,
        tom = dato,
        beløp = utbetaltBeløp,
        vedtakssats = sats,
    )
}

fun aapUId(sakId: String, meldeperiode: String, stønad: StønadTypeAAP): UtbetalingId {
    return UtbetalingId(uuid(SakId(sakId), Fagsystem.AAP, meldeperiode, stønad))
}

data class AapTuple(val key: String, val value: AapUtbetaling)

fun toDomain(
    key: String,
    value: AapUtbetaling,
    uidsPåSak: Set<UtbetalingId>?,
    uid: UtbetalingId,
): Utbetaling {
    return Utbetaling(
        dryrun = value.dryrun,
        originalKey = key,
        fagsystem = Fagsystem.AAP,
        uid = uid,
        action = Action.CREATE,
        førsteUtbetalingPåSak = uidsPåSak == null,
        sakId = SakId(value.sakId),
        behandlingId = BehandlingId(value.behandlingId),
        lastPeriodeId = PeriodeId(),
        personident = Personident(value.ident),
        vedtakstidspunkt = value.vedtakstidspunktet,
        stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
        beslutterId = Navident("kelvin"),
        saksbehandlerId = Navident("kelvin"),
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = perioder(value.utbetalinger),
    )
}

private fun perioder(perioder: List<AapUtbetalingsdag>): List<Utbetalingsperiode> {
    return perioder.sortedBy { it.dato }
        .groupBy { listOf(it.utbetaltBeløp, it.sats) }
        .map { (_, p) ->
            p.splitWhen { a, b -> a.dato.nesteUkedag() != b.dato }.map {
                Utbetalingsperiode(
                    fom = it.first().dato,
                    tom = it.last().dato,
                    beløp = it.first().utbetaltBeløp,
                    vedtakssats = it.first().sats,
                )
            }
        }.flatten()
}
