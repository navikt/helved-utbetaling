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
    val saksbehandler: String? = null,
    val beslutter: String? = null,
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
        beslutterId = value.beslutter?.let(::Navident) ?: Navident("kelvin"),
        saksbehandlerId = value.saksbehandler?.let(::Navident) ?: Navident("kelvin"),
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = perioder(value.utbetalinger),
    )
}

private fun perioder(perioder: List<AapUtbetalingsdag>): List<Utbetalingsperiode> {
    return perioder.sortedBy { it.dato }
        .groupBy { listOf(it.utbetaltBeløp, it.sats) }
        .map { (_, p) ->
            p.splitWhen { a, b -> 
                val harSammenhengendeDager = a.dato.plusDays(1) == b.dato
                val harSammenhengendeUker = a.dato.nesteUkedag() == b.dato
                !harSammenhengendeUker && !harSammenhengendeDager
            }.map {
                Utbetalingsperiode(
                    fom = it.first().dato,
                    tom = it.last().dato,
                    beløp = it.first().utbetaltBeløp,
                    vedtakssats = it.first().sats,
                )
            }
        }.flatten()
}
