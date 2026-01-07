package models

import java.time.LocalDate
import java.time.LocalDateTime

data class TpUtbetaling(
    val sakId: String,
    val behandlingId: String,
    val dryrun: Boolean = false,
    val personident: String,
    val vedtakstidspunkt: LocalDateTime,
    val perioder: List<TpPeriode>,
    val saksbehandler: String? = null,
    val beslutter: String? = null,
)

data class TpPeriode(
    val meldeperiode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val betalendeEnhet: NavEnhet? = null,
    val beløp: UInt,
    val stønad: StønadTypeTiltakspenger,
    ) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        betalendeEnhet = betalendeEnhet,
        beløp = beløp,
    )
}

data class TpTuple(val key: String, val dto: TpUtbetaling)

fun tpUId(sakId: String, meldeperiode: String, stønad: StønadTypeTiltakspenger): UtbetalingId {
    return UtbetalingId(uuid(SakId(sakId), Fagsystem.TILTAKSPENGER, meldeperiode, stønad))
}

fun toDomain(
    key: String,
    value: TpUtbetaling,
    uidsPåSak: Set<UtbetalingId>?,
    uid: UtbetalingId,
): Utbetaling {
    val stønad = value.perioder.first().stønad
    require(value.perioder.all { it.stønad == stønad })

    return Utbetaling(
        dryrun = value.dryrun,
        originalKey = key,
        fagsystem = Fagsystem.TILTAKSPENGER,
        uid = uid,
        action = Action.CREATE,
        førsteUtbetalingPåSak = uidsPåSak == null,
        sakId = SakId(value.sakId),
        behandlingId = BehandlingId(value.behandlingId),
        lastPeriodeId = PeriodeId(),
        personident = Personident(value.personident),
        vedtakstidspunkt = value.vedtakstidspunkt,
        stønad = stønad,
        beslutterId = value.beslutter?.let(::Navident) ?: Navident("tp"),
        saksbehandlerId = value.saksbehandler?.let(::Navident) ?: Navident("tp"),
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = value.perioder.toDomain(),
    )
}

private fun List<TpPeriode>.toDomain(): List<Utbetalingsperiode> {
    return this
        .groupBy { it.beløp }
        .map { (_, perioder) ->
            perioder.splitWhen { cur, next ->
                val harSammenhengendeDager = cur.tom.plusDays(1).equals(next.fom)
                val harSammenhengendeUker = cur.tom.nesteUkedag().equals(next.fom)
                !harSammenhengendeUker && !harSammenhengendeDager
            }.map {
                Utbetalingsperiode(
                    fom = it.first().fom,
                    tom = it.last().tom,
                    betalendeEnhet = it.last().betalendeEnhet,
                    beløp = it.first().beløp,
                )
            }
        }
        .flatten()
        .sortedBy { it.fom }
}

