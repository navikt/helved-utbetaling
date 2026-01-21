package models

import libs.utils.appLog
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
    val barnetillegg: Boolean = false,
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

fun perioder(perioder: List<TpPeriode>): List<Utbetalingsperiode> {
    return perioder
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

fun tpUId(sakId: String, meldeperiode: String, stønad: StønadTypeTiltakspenger): UtbetalingId {
    val uuid = uuid(SakId(sakId), Fagsystem.TILTAKSPENGER, meldeperiode, stønad)
    return UtbetalingId(uuid)
}

object TpDto {
    fun splitToDomain(
        sakId: SakId,
        originalKey: String,
        tpUtbetaling: TpUtbetaling,
        uids: Set<UtbetalingId>?,
    ): List<Utbetaling> {
        val dryrun = tpUtbetaling.dryrun
        val personident = tpUtbetaling.personident
        val behandlingId = tpUtbetaling.behandlingId
        val periodetype = Periodetype.UKEDAG
        val vedtakstidspunktet = tpUtbetaling.vedtakstidspunkt
        val beslutterId = tpUtbetaling.beslutter ?: "tp"
        val saksbehandler = tpUtbetaling.saksbehandler ?: "tp"
        val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, TpUtbetaling?>> = tpUtbetaling
            .perioder
            .groupBy { it.meldeperiode to it.stønad.medBarnetillegg(it.barnetillegg) }
            .map { (group, utbetalinger) ->
                val (meldeperiode, stønad) = group
                tpUId(tpUtbetaling.sakId, meldeperiode, stønad) to tpUtbetaling.copy(perioder = utbetalinger)
            }
            .toMutableList()

        if (uids != null) {
            val tpUids = utbetalingerPerMeldekort.map { (tpUid, _) -> tpUid }
            val missingMeldeperioder = uids.filter { it !in tpUids }.map { it to null }
            utbetalingerPerMeldekort.addAll(missingMeldeperioder)
        }

        return utbetalingerPerMeldekort.map { (uid, tpUtbetaling) ->
            when (tpUtbetaling) {
                null -> fakeDelete(
                    dryrun = dryrun,
                    originalKey = originalKey,
                    sakId = sakId,
                    uid = uid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, // Dette er en placeholder
                    beslutterId = Navident(beslutterId),
                    saksbehandlerId = Navident(saksbehandler),
                    personident = Personident(personident),
                    behandlingId = BehandlingId(behandlingId),
                    periodetype = periodetype,
                    vedtakstidspunkt = vedtakstidspunktet,
                ).also { appLog.debug("creating a fake delete to force-trigger a join with existing utbetaling") }

                else -> utbetaling(originalKey, tpUtbetaling, uids, uid)
            }
        }
    }

    private fun utbetaling(
        key: String,
        value: TpUtbetaling,
        uidsPåSak: Set<UtbetalingId>?,
        uid: UtbetalingId,
    ): Utbetaling {
        val stønad = value.perioder.first().stønad.medBarnetillegg(value.perioder.first().barnetillegg)
        require(value.perioder.all { it.stønad.medBarnetillegg(it.barnetillegg) == stønad })

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
            perioder = perioder(value.perioder),
        )
    }
}

