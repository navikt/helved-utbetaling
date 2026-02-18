package models

import libs.utils.appLog
import java.time.LocalDate
import java.time.LocalDateTime

data class DpUtbetaling(
    val dryrun: Boolean = false,

    val sakId: String,
    val behandlingId: String,
    val ident: String,
    val utbetalinger: List<DpUtbetalingsdag>,
    val vedtakstidspunktet: LocalDateTime,
    val saksbehandler: String? = null,
    val beslutter: String? = null,
)

enum class Utbetalingstype {
    DagpengerFerietillegg,
    Dagpenger,
}

fun DpUtbetalingsdag.stønadstype(): StønadTypeDagpenger {
    return when (utbetalingstype) {
        Utbetalingstype.Dagpenger -> StønadTypeDagpenger.DAGPENGER
        Utbetalingstype.DagpengerFerietillegg -> StønadTypeDagpenger.DAGPENGERFERIE
    }
}

data class DpUtbetalingsdag(
    val meldeperiode: String,
    val dato: LocalDate,
    val sats: UInt,
    val utbetaltBeløp: UInt,
    val utbetalingstype: Utbetalingstype,
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = dato,
        tom = dato,
        beløp = utbetaltBeløp,
        vedtakssats = sats,
    )
}

fun dpUId(sakId: String, meldeperiode: String, stønad: StønadTypeDagpenger): UtbetalingId {
    val uuid = uuid(SakId(sakId), Fagsystem.DAGPENGER, meldeperiode, stønad)
    return UtbetalingId(uuid) 
}

private fun perioder(perioder: List<DpUtbetalingsdag>): List<Utbetalingsperiode> {
    return perioder
        .sortedBy { it.dato }
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

object DpDto {
    fun splitToDomain(
        sakId: SakId,
        originalKey: String,
        dpUtbetaling: DpUtbetaling,
        uids: Set<UtbetalingId>?,
    ): List<Utbetaling> {
        val dryrun = dpUtbetaling.dryrun
        val personident = dpUtbetaling.ident
        val behandlingId = dpUtbetaling.behandlingId
        val periodetype = Periodetype.UKEDAG
        val vedtakstidspunktet = dpUtbetaling.vedtakstidspunktet
        val beslutterId = dpUtbetaling.beslutter ?: "dagpenger"
        val saksbehandler = dpUtbetaling.saksbehandler ?: "dagpenger"
        val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, DpUtbetaling?>> = dpUtbetaling
            .utbetalinger
            .groupBy { it.meldeperiode to it.stønadstype() }
            .map { (group, utbetalinger) ->
                val (meldeperiode, stønadstype) = group
                dpUId(dpUtbetaling.sakId, meldeperiode, stønadstype) to dpUtbetaling.copy(utbetalinger = utbetalinger)
            }
            .toMutableList()

        if (uids != null) {
            val dpUids = utbetalingerPerMeldekort.map { (dpUid, _) -> dpUid }
            val missingMeldeperioder = uids.filter { it !in dpUids }.map { it to null }
            utbetalingerPerMeldekort.addAll(missingMeldeperioder)
        }

        return utbetalingerPerMeldekort.map { (uid, utbet) ->
            when (utbet) {
                null -> fakeDelete(
                    dryrun = dryrun,
                    originalKey = originalKey,
                    sakId = sakId,
                    uid = uid,
                    fagsystem = Fagsystem.DAGPENGER,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    beslutterId = Navident(beslutterId),
                    saksbehandlerId = Navident(saksbehandler),
                    personident = Personident(personident),
                    behandlingId = BehandlingId(behandlingId),
                    periodetype = periodetype,
                    vedtakstidspunkt = vedtakstidspunktet,
                ).also { appLog.info("creating a fake delete to force-trigger a join with existing utbetaling") }

                else -> utbetaling(originalKey, utbet, uids, uid)
            }
        }
    }

    private fun utbetaling(
        key: String,
        value: DpUtbetaling,
        uidsPåSak: Set<UtbetalingId>?,
        uid: UtbetalingId,
    ): Utbetaling {
        val stønad = value.utbetalinger.first().stønadstype()
        require(value.utbetalinger.all { it.stønadstype() == stønad })

        return Utbetaling(
            dryrun = value.dryrun,
            originalKey = key,
            fagsystem = Fagsystem.DAGPENGER,
            uid = uid,
            action = Action.CREATE,
            førsteUtbetalingPåSak = uidsPåSak == null,
            sakId = SakId(value.sakId),
            behandlingId = BehandlingId(value.behandlingId),
            lastPeriodeId = PeriodeId(),
            personident = Personident(value.ident),
            vedtakstidspunkt = value.vedtakstidspunktet,
            stønad = stønad,
            beslutterId = value.beslutter?.let(::Navident) ?: Navident("dagpenger"),
            saksbehandlerId = value.saksbehandler?.let(::Navident) ?: Navident("dagpenger"),
            periodetype = Periodetype.UKEDAG,
            avvent = null,
            perioder = perioder(value.utbetalinger),
        )
    }
}


