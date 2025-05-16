package abetal.models

import abetal.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import models.*

data class DpUtbetaling(
    val dryrun: Boolean = false,
    val behandlingId: String,
    val fagsakId: String,
    val ident: String,
    val vedtakstidspunkt: LocalDateTime,
    val virkningsdato: LocalDate,
    val behandletHendelse: Hendelse,
    val utbetalinger: List<DpUtbetalingsperiode>,
    val stønad: StønadTypeDagpenger,
)

data class Hendelse(
    val datatype: String = "Long",
    val id: String, 
    val type: String = "Meldekort",
)

data class DpUtbetalingsperiode(
    val meldeperiode: String,
    val dato: LocalDate,
    val sats: UInt,
    val utbetaling: UInt,
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = dato,
        tom = dato,
        beløp = utbetaling,
        vedtakssats = sats,
        betalendeEnhet = null,
    )
}

fun dpUId(sakId: String, meldeperiode: String): UtbetalingId {
    return UtbetalingId(uuid(SakId(sakId), Fagsystem.DAGPENGER, meldeperiode))
}

fun toDomain(
    key: String,
    value: DpUtbetaling,
    sakValue: SakValue?,
    uid: UtbetalingId,
): Utbetaling {
    return Utbetaling(
        dryrun = value.dryrun,
        originalKey = key,
        fagsystem = Fagsystem.DAGPENGER,
        uid = uid,
        action = Action.CREATE, // TODO: utled
        førsteUtbetalingPåSak = sakValue == null,
        utbetalingerPåSak = sakValue?.uids ?: emptySet(),
        sakId = SakId(value.fagsakId),
        behandlingId = BehandlingId(value.behandlingId),
        lastPeriodeId = PeriodeId(),
        personident = Personident(value.ident),
        vedtakstidspunkt = value.vedtakstidspunkt,
        stønad = value.stønad,
        beslutterId = Navident("dagpenger"), // FIXME: navnet på systemet
        saksbehandlerId = Navident("dagpenger"), // FIXME: navnet på systemet
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = value.utbetalinger.map { it.into() },
    )
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

