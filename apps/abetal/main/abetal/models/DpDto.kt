package abetal.models

import abetal.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import libs.kafka.*
import libs.utils.appLog
import models.*

data class DpUtbetaling(
    val dryrun: Boolean = false,

    val sakId: String,
    val behandlingId: String,
    val ident: String,
    val utbetalinger: List<DpUtbetalingsdag>,

    val vedtakstidspunktet: LocalDateTime,
    val type: Rettighetstype,
)

enum class Rettighetstype {
    Ordinær,
    Permittering,
    PermitteringFiskeindustrien,
    EØS,
}

fun Rettighetstype.into(): StønadTypeDagpenger {
    return when (this) {
        Rettighetstype.Ordinær -> StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR
        Rettighetstype.Permittering -> StønadTypeDagpenger.PERMITTERING_ORDINÆR
        Rettighetstype.PermitteringFiskeindustrien -> StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI
        Rettighetstype.EØS -> StønadTypeDagpenger.EØS
    }
}

data class DpUtbetalingsdag(
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

fun dpUId(sakId: String, meldeperiode: String): UtbetalingId {
    return UtbetalingId(uuid(SakId(sakId), Fagsystem.DAGPENGER, meldeperiode))
}

fun splitOnMeldeperiode(sakKey: SakKey, tuple: DpTuple, uids: Set<UtbetalingId>?): List<KeyValue<String, Utbetaling>> {
    val (dpKey, dpUtbetaling) = tuple
    val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, DpUtbetaling?>> = dpUtbetaling 
        .utbetalinger
        .groupBy { it.meldeperiode }
        .map { (meldeperiode, utbetalinger) -> dpUId(dpUtbetaling.sakId, meldeperiode) to dpUtbetaling.copy(utbetalinger = utbetalinger) }
        .toMutableList()

    if (uids != null) {
        val dpUids = utbetalingerPerMeldekort.map { (dpUid, _) -> dpUid }
        val missingMeldeperioder = uids.filter { it !in dpUids }.map { it to null }
        utbetalingerPerMeldekort.addAll(missingMeldeperioder)
    }

   return utbetalingerPerMeldekort.map { (uid, dpUtbetaling) -> 
        val utbetaling = when (dpUtbetaling) {
            null -> fakeDelete(dpKey, sakKey.sakId, uid).also { appLog.info("creating a fake delete to force-trigger a join with existing utbetaling") }
            else -> toDomain(dpKey, dpUtbetaling, uids, uid)
        }
        appLog.info("rekey to ${utbetaling.uid.id} and left join with ${Topics.utbetalinger.name}")
        KeyValue(utbetaling.uid.id.toString(), utbetaling)
   }
}

fun toDomain(
    key: String,
    value: DpUtbetaling,
    uidsPåSak: Set<UtbetalingId>?,
    uid: UtbetalingId,
): Utbetaling {
    return Utbetaling(
        dryrun = value.dryrun,
        originalKey = key,
        fagsystem = Fagsystem.DAGPENGER,
        uid = uid,
        action = Action.CREATE,
        førsteUtbetalingPåSak = uidsPåSak == null,
        utbetalingerPåSak = uidsPåSak ?: emptySet(), // hvis lista null er det første utbetaling, hvis lista er tom har det være en delete der før
        sakId = SakId(value.sakId),
        behandlingId = BehandlingId(value.behandlingId),
        lastPeriodeId = PeriodeId(),
        personident = Personident(value.ident),
        vedtakstidspunkt = value.vedtakstidspunktet,
        stønad = value.type.into(),
        beslutterId = Navident("dagpenger"),
        saksbehandlerId = Navident("dagpenger"),
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = perioder(value.utbetalinger), //.map { it.into() },
    )
}

private fun perioder(perioder: List<DpUtbetalingsdag>): List<Utbetalingsperiode> {
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

private fun <T> List<T>.splitWhen(predicate: (T, T) -> Boolean): List<List<T>> {
    if (this.isEmpty()) return emptyList()

    return this.drop(1).fold(mutableListOf(mutableListOf(this.first()))) { acc, item ->
        val lastSublist = acc.last()
        if (predicate(lastSublist.last(), item)) {
            acc.add(mutableListOf(item))
        } else {
            lastSublist.add(item)
        }
        acc
    }.map { it.toList() }
}

fun fakeDelete(
    originalKey: String,
    sakId: SakId,
    uid: UtbetalingId,
) = Utbetaling(
    dryrun = false,
    originalKey = originalKey,
    fagsystem = Fagsystem.DAGPENGER,
    uid = uid,
    action = Action.DELETE,
    førsteUtbetalingPåSak = false,
    utbetalingerPåSak = emptySet(),
    sakId = sakId,
    behandlingId = BehandlingId(""),
    lastPeriodeId = PeriodeId(),
    personident = Personident(""),
    vedtakstidspunkt = LocalDateTime.now(),
    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    beslutterId = Navident("dagpenger"), 
    saksbehandlerId = Navident("dagpenger"),
    periodetype = Periodetype.UKEDAG,
    avvent = null,
    perioder = emptyList(),
)

data class DpTuple(val key: String, val value: DpUtbetaling)

fun uuid(sakId: SakId, fagsystem: Fagsystem, meldeperiode: String): UUID {
    val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
    buffer.putLong((fagsystem.name + sakId.id + meldeperiode).hashCode().toLong())

    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(buffer.array())

    val bb = ByteBuffer.wrap(hash)
    val mostSigBits = bb.long
    val leastSigBits = bb.long

    return UUID(mostSigBits, leastSigBits)
}

