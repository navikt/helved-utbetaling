package models

import java.lang.Long
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class DpUtbetaling(
    val dryrun: Boolean = false,

    val sakId: String,
    val behandlingId: String,
    val ident: String,
    val utbetalinger: List<DpUtbetalingsdag>,
    val vedtakstidspunktet: LocalDateTime,
)

enum class Utbetalingstype {
    DagpengerFerietillegg,
    Dagpenger,
}

enum class Rettighetstype {
    Ordinær,
    Permittering,
    PermitteringFiskeindustrien,
    EØS,
}

fun DpUtbetalingsdag.stønadstype(): StønadTypeDagpenger {
    return when (utbetalingstype) {
        Utbetalingstype.Dagpenger -> {
            when (rettighetstype) {
                Rettighetstype.Ordinær -> StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR
                Rettighetstype.Permittering -> StønadTypeDagpenger.PERMITTERING_ORDINÆR
                Rettighetstype.PermitteringFiskeindustrien -> StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI
                Rettighetstype.EØS -> StønadTypeDagpenger.EØS
            }
        }
        Utbetalingstype.DagpengerFerietillegg -> {
            when (rettighetstype) {
                Rettighetstype.Ordinær -> StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG
                Rettighetstype.Permittering -> StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG
                Rettighetstype.PermitteringFiskeindustrien -> StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI_FERIETILLEGG
                Rettighetstype.EØS -> StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG
            }
        }
    }
}

data class DpUtbetalingsdag(
    val meldeperiode: String,
    val dato: LocalDate,
    val sats: UInt,
    val utbetaltBeløp: UInt,
    val rettighetstype: Rettighetstype,
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
    return UtbetalingId(uuid(SakId(sakId), Fagsystem.DAGPENGER, meldeperiode, stønad))
}

fun toDomain(
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

data class DpTuple(val key: String, val value: DpUtbetaling)

// fun uuid(
//     sakId: SakId,
//     fagsystem: Fagsystem,
//     meldeperiode: String,
//     stønad: StønadTypeDagpenger,
// ): UUID {
//     val buffer = ByteBuffer.allocate(Long.BYTES)
//     buffer.putLong((fagsystem.name + sakId.id + meldeperiode + stønad.klassekode).hashCode().toLong())
//
//     val digest = MessageDigest.getInstance("SHA-256")
//     val hash = digest.digest(buffer.array())
//
//     val bb = ByteBuffer.wrap(hash)
//     val mostSigBits = bb.long
//     val leastSigBits = bb.long
//
//     return UUID(mostSigBits, leastSigBits)
// }

