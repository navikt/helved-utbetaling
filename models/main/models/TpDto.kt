package models

import java.time.LocalDate
import java.time.LocalDateTime

data class TpUtbetaling(
    val dryrun: Boolean = false,
    val sakId: String,
    val behandlingId: String,
    val personident: String,
    val stønad: StønadTypeTiltakspenger,
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
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        betalendeEnhet = betalendeEnhet,
        beløp = beløp,
    )
}

data class TpTuple(val transactionId: String, val dto: TpUtbetaling)

fun tpUId(sakId: String, meldeperiode: String, stønad: StønadTypeTiltakspenger): UtbetalingId {
    return UtbetalingId(uuid(SakId(sakId), Fagsystem.TILTAKSPENGER, meldeperiode, stønad))
}

fun TpTuple.toDomain(
    uid: UtbetalingId,
    uidsPåSak: Set<UtbetalingId>?,
): Utbetaling {
    return Utbetaling(
        dryrun = dto.dryrun,
        originalKey = transactionId,
        fagsystem = Fagsystem.TILTAKSPENGER,
        uid = uid,
        action = Action.CREATE,
        førsteUtbetalingPåSak = uidsPåSak == null,
        sakId = SakId(dto.sakId),
        behandlingId = BehandlingId(dto.behandlingId),
        lastPeriodeId = PeriodeId(),
        personident = Personident(dto.personident),
        vedtakstidspunkt = dto.vedtakstidspunkt,
        stønad = dto.stønad,
        beslutterId = dto.beslutter?.let(::Navident) ?: Navident("ts"),
        saksbehandlerId = dto.saksbehandler?.let(::Navident) ?: Navident("ts"),
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = dto.perioder.toDomain(),
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
        }.flatten()
}

