package models

import libs.utils.appLog
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
    val uuid = uuid(SakId(sakId), Fagsystem.AAP, meldeperiode, stønad)
    return UtbetalingId(uuid)
}

private fun perioder(perioder: List<AapUtbetalingsdag>): List<Utbetalingsperiode> {
    return perioder.sortedBy { it.dato }
        .groupBy { listOf(it.utbetaltBeløp, it.sats) }
        .map { (_, p) ->
            p.splitWhen { a, b -> 
                val harSammenhengendeDager = a.dato.plusDays(1).equals(b.dato)
                val harSammenhengendeUker = a.dato.nesteUkedag().equals(b.dato)
                !harSammenhengendeUker && !harSammenhengendeDager
            }.map {
                Utbetalingsperiode(
                    fom = it.first().dato,
                    tom = it.last().dato,
                    beløp = it.first().utbetaltBeløp,
                    vedtakssats = it.first().sats,
                )
            }
        }
        .flatten()
        .sortedBy { it.fom }
}

object AapDto {
    fun splitToDomain(
        sakId: SakId,
        originalKey: String,
        aapUtbetaling: AapUtbetaling,
        uids: Set<UtbetalingId>?
    ): List<Utbetaling> {
        val dryrun = aapUtbetaling.dryrun
        val personident = aapUtbetaling.ident
        val behandlingId = aapUtbetaling.behandlingId
        val periodetype = Periodetype.UKEDAG
        val vedtakstidspunktet = aapUtbetaling.vedtakstidspunktet
        val beslutterId = aapUtbetaling.beslutter ?: "kelvin"
        val saksbehandler = aapUtbetaling.saksbehandler ?: "kelvin"
        val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, AapUtbetaling?>> = aapUtbetaling
            .utbetalinger
            .groupBy { it.meldeperiode }
            .map { (meldeperiode, utbetalinger) ->
                aapUId(aapUtbetaling.sakId, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING) to aapUtbetaling.copy(
                    utbetalinger = utbetalinger
                )
            }
            .toMutableList()

        if (uids != null) {
            val aapUids = utbetalingerPerMeldekort.map { (aapUid, _) -> aapUid }
            val missingMeldeperioder = uids.filter { it !in aapUids }.map { it to null }
            utbetalingerPerMeldekort.addAll(missingMeldeperioder)
        }

        return utbetalingerPerMeldekort.map { (uid, aapUtbetaling) ->
            when (aapUtbetaling) {
                null -> fakeDelete(
                    dryrun = dryrun,
                    originalKey = originalKey,
                    sakId = sakId,
                    uid = uid,
                    fagsystem = Fagsystem.AAP,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    beslutterId = Navident(beslutterId),
                    saksbehandlerId = Navident(saksbehandler),
                    personident = Personident(personident),
                    behandlingId = BehandlingId(behandlingId),
                    periodetype = periodetype,
                    vedtakstidspunkt = vedtakstidspunktet,
                ).also { appLog.debug("creating a fake delete to " + "force-trigger a join with existing utbetaling") }

                else -> utbetaling(originalKey, aapUtbetaling, uids, uid)
            }
        }
    }

    private fun utbetaling(
        originalKey: String,
        value: AapUtbetaling,
        uidsPåSak: Set<UtbetalingId>?,
        uid: UtbetalingId,
    ): Utbetaling {
        return Utbetaling(
            dryrun = value.dryrun,
            originalKey = originalKey,
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

}
