package models

import com.fasterxml.jackson.annotation.JsonCreator
import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.*
import kotlin.getOrThrow
import libs.utils.secureLog
import libs.utils.appLog

@JvmInline value class SakId(val id: String) { override fun toString(): String = id }
@JvmInline value class BehandlingId(val id: String) { override fun toString(): String = id }
@JvmInline value class NavEnhet(val enhet: String) { override fun toString(): String = enhet }
@JvmInline value class Personident(val ident: String) { override fun toString(): String = ident }
@JvmInline value class Navident(val ident: String) { override fun toString(): String = ident }
@JvmInline value class UtbetalingId(val id: UUID) { override fun toString(): String = id.toString() }

data class Utbetaling(
    val dryrun: Boolean,
    val originalKey: String,
    val fagsystem: Fagsystem,
    val uid: UtbetalingId,
    val action: Action,
    val førsteUtbetalingPåSak: Boolean,
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val lastPeriodeId: PeriodeId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: Stønadstype,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    val periodetype: Periodetype,
    val avvent: Avvent?,
    val perioder: List<Utbetalingsperiode>,
) {
    fun validate() {
        failOnEmptyPerioder()
        failOnÅrsskifte()
        failOnDuplicatePerioder()
        failOnTomBeforeFom()
        failOnIllegalFutureUtbetaling()
        failOnTooManyPeriods()
        failOnZeroBeløp()
        failOnTooLongSakId()
        failOnTooLongBehandlingId()
        //failOnWeekendInPeriodetypeDag()
    }

    fun isDuplicate(other: Utbetaling?): Boolean {
        if (other == null) {
            appLog.info("existing utbetaling for $uid was null")
            return false
        }

        val isDuplicate = uid == other.uid
            && action == other.action 
            && sakId == other.sakId 
            && behandlingId == other.behandlingId 
            && personident == other.personident
            && vedtakstidspunkt.equals(other.vedtakstidspunkt)
            && stønad == other.stønad
            && beslutterId == other.beslutterId
            && saksbehandlerId == other.saksbehandlerId
            && periodetype == other.periodetype
            && avvent == other.avvent
            && perioder == other.perioder


        if (isDuplicate) {
            appLog.info("Duplicate message found for $uid")
            return isDuplicate
        }

        return isDuplicate
    }
}


enum class Årsak(val kode: String) {
    AVVENT_AVREGNING("AVAV"),
    AVVENT_REFUSJONSKRAV("AVRK"),
}

data class Avvent(
    val fom: LocalDate,
    val tom: LocalDate,
    val overføres: LocalDate? = null,
    val årsak: Årsak? = null,
    val feilregistrering: Boolean = false,
)

fun Utbetaling.failOnEmptyPerioder() {
    if (perioder.isEmpty()) {
        badRequest(
            msg = "perioder kan ikke være tom",
            doc = "opprett_en_utbetaling"
        )
    }
}

fun Utbetaling.validateLockedFields(other: Utbetaling) {
    if (sakId != other.sakId) badRequest("cant change immutable field 'sakId'")
    if (personident != other.personident) badRequest("cant change immutable field 'personident'")
    if (stønad != other.stønad) badRequest("cant change immutable field")
    if (periodetype != other.periodetype) badRequest("can't change periodetype", "opprett_en_utbetaling")
}

fun Utbetaling.validateMinimumChanges(other: Utbetaling) {
    if (perioder.size != other.perioder.size) return
    val ingenEndring = perioder.sortedBy { it.hashCode() }.zip(other.perioder.sortedBy { it.hashCode() }).all { (l, r) -> l.beløp == r.beløp && l.fom.equals(r.fom) && l.tom.equals(r.tom) && l.vedtakssats == r.vedtakssats }
    if (ingenEndring) conflict("periods already exists", "opprett_en_utbetaling")
}

fun Utbetaling.failOnÅrsskifte() {
    if (periodetype != Periodetype.EN_GANG) return
    if (perioder.minBy { it.fom }.fom.year != perioder.maxBy { it.tom }.tom.year) {
        badRequest("periode strekker seg over årsskifte", "opprett_en_utbetaling")
    }
}

fun Utbetaling.failOnZeroBeløp() {
    if (perioder.any { it.beløp == 0u }) {
        badRequest("beløp kan ikke være 0", "opprett_en_utbetaling")
    }
}

fun Utbetaling.failOnDuplicatePerioder() {
    val dupFom = perioder.groupBy { it.fom }.any { (_, perioder) -> perioder.size != 1 }
    if (dupFom) badRequest("kan ikke sende inn duplikate perioder", "opprett_en_utbetaling#Duplikate%20perioder")
    val dupTom = perioder.groupBy { it.tom }.any { (_, perioder) -> perioder.size != 1 }
    if (dupTom) badRequest("kan ikke sende inn duplikate perioder", "opprett_en_utbetaling#Duplikate%20perioder")
}

fun Utbetaling.failOnTomBeforeFom() {
    if (!perioder.all { it.fom <= it.tom }) badRequest("fom må være før eller lik tom", "opprett_en_utbetaling")
}

fun Utbetaling.failOnIllegalFutureUtbetaling() {
    if (stønad is StønadTypeTilleggsstønader) return
    val isDay = periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG) 
    val dayIsFuture = perioder.maxBy{ it.tom }.tom.isAfter(LocalDate.now()) 
    if (isDay && dayIsFuture) badRequest(
        "fremtidige utbetalinger er ikke støttet for periode dag/ukedag",
        "opprett_en_utbetaling"
    )
}

fun Utbetaling.failOnTooManyPeriods() {
    if (periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG)) {
        val min = perioder.minBy { it.fom }.fom
        val max = perioder.maxBy { it.tom }.tom
        val tooManyPeriods = java.time.temporal.ChronoUnit.DAYS.between(min, max)+1 > 1000 
        if (tooManyPeriods) badRequest("$periodetype støtter maks periode på 1000 dager", "opprett_en_utbetaling")
    }
}

fun Utbetaling.failOnTooLongSakId() {
    if (sakId.id.length > 30) {
        badRequest("sakId kan være maks 30 tegn langt", "opprett_en_utbetaling")
    }
}

fun Utbetaling.failOnTooLongBehandlingId() {
    if (behandlingId.id.length > 30) {
        badRequest("behandlingId kan være maks 30 tegn langt", "opprett_en_utbetaling")
    }
}

fun Utbetaling.failOnWeekendInPeriodetypeDag() {
    if (periodetype == Periodetype.UKEDAG) {

        val harHelgedager = perioder.any { periode ->
            generateSequence(periode.fom) { it.plusDays(1) }
                .takeWhile { !it.isAfter(periode.tom) }
                .any { it.erHelg() }
        }

        if (harHelgedager) {
            badRequest(
                msg = "periodetype DAG kan ikke inneholde helgedager (lørdag/søndag)",
                doc = "opprett_en_utbetaling"
            )
        }
    }
}

@JvmInline
value class PeriodeId (private val id: UUID) {
    constructor() : this(UUID.randomUUID())

    init { 
        toString() // verifiser at encoding blir under 30 tegn
    }
    companion object {
        fun decode(encoded: String): PeriodeId {
            try {
                val byteBuffer: ByteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded))
                // ||||||||||||||||||||||||||||||||||||||||||||||||||||||||
                // ^ les neste 64 og lag en long
                return PeriodeId(UUID(byteBuffer.long, byteBuffer.long))
            } catch (e: Throwable) {
                appLog.warn("Klarte ikke dekomprimere UUID: $encoded")
                secureLog.warn("Klarte ikke dekomprimere UUID: $encoded", e)
                throw e
            }
        }
    }

    /**
     * UUID er 128 bit eller 36 tegn
     * Oppdrag begrenses til 30 tegn (eller 240 bits)
     * To Longs er 128 bits
     */
    override fun toString(): String {
        val byteBuffer = ByteBuffer.allocate(java.lang.Long.BYTES * 2).apply { // 128 bits
            putLong(id.mostSignificantBits) // første 64 bits
            putLong(id.leastSignificantBits) // siste 64 bits
        }

        // e.g. dNl8DVZKQM2gJ0AcJ/pNKQ== (24 tegn)
        return Base64.getEncoder().encodeToString(byteBuffer.array()).also {
            require(it.length <= 30) { "base64 encoding av UUID ble over 30 tegn." }
        }
    }
}

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val betalendeEnhet: NavEnhet? = null,
    val vedtakssats: UInt? = null,
) 

fun List<Utbetalingsperiode>.betalendeEnhet(): NavEnhet? {
    return sortedBy { it.tom }.find { it.betalendeEnhet != null }?.betalendeEnhet
}

enum class Periodetype(val satstype: String) {
    DAG("DAG7"),
    UKEDAG("DAG"),
    MND("MND"),
    EN_GANG("ENG");
}

sealed interface Stønadstype {
    val name: String
    val klassekode: String

    companion object {
        @JsonCreator
        @JvmStatic
        fun valueOf(str: String): Stønadstype =
            runCatching { StønadTypeDagpenger.valueOf(str) }
                .recoverCatching { StønadTypeTilleggsstønader.valueOf(str) }
                .recoverCatching { StønadTypeTiltakspenger.valueOf(str) }
                .recoverCatching { StønadTypeAAP.valueOf(str) }
                .getOrThrow()

        fun fraKode(klassekode: String): Stønadstype =
            (StønadTypeDagpenger.entries + StønadTypeTiltakspenger.entries + StønadTypeTilleggsstønader.entries + StønadTypeAAP.entries)
                .single { it.klassekode == klassekode }
    }
}

enum class StønadTypeDagpenger(override val klassekode: String) : Stønadstype {
    DAGPENGER("DAGPENGER"),
    DAGPENGERFERIE("DAGPENGERFERIE")

}

enum class StønadTypeTiltakspenger(override val klassekode: String) : Stønadstype {
    ARBEIDSFORBEREDENDE_TRENING("TPTPAFT"),
    ARBEIDSRETTET_REHABILITERING("TPTPARREHABAGDAG"),
    ARBEIDSTRENING("TPTPATT"),
    AVKLARING("TPTPAAG"),
    DIGITAL_JOBBKLUBB("TPTPDJB"),
    ENKELTPLASS_AMO("TPTPEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG("TPTPEPVGSHOU"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET("TPTPFLV"),
    GRUPPE_AMO("TPTPGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG("TPTPGRVGSHOY"),
    HØYERE_UTDANNING("TPTPHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE("TPTPIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG("TPTPIPSUNG"),
    JOBBKLUBB("TPTPJK2009"),
    OPPFØLGING("TPTPOPPFAG"),
    UTVIDET_OPPFØLGING_I_NAV("TPTPUAOPPF"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING("TPTPUOPPFOPPL"),

    ARBEIDSFORBEREDENDE_TRENING_BARN("TPBTAF"),
    ARBEIDSRETTET_REHABILITERING_BARN("TPBTARREHABAGDAG"),
    ARBEIDSTRENING_BARN("TPBTATTILT"),
    AVKLARING_BARN("TPBTAAGR"),
    DIGITAL_JOBBKLUBB_BARN("TPBTDJK"),
    ENKELTPLASS_AMO_BARN("TPBTEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN("TPBTEPVGSHOY"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN("TPBTFLV"),
    GRUPPE_AMO_BARN("TPBTGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN("TPBTGRVGSHOY"),
    HØYERE_UTDANNING_BARN("TPBTHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE_BARN("TPBTIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG_BARN("TPBTIPSUNG"),
    JOBBKLUBB_BARN("TPBTJK2009"),
    OPPFØLGING_BARN("TPBTOPPFAGR"),
    UTVIDET_OPPFØLGING_I_NAV_BARN("TPBTUAOPPFL"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN("TPBTUOPPFOPPL"),
}

enum class StønadTypeTilleggsstønader(override val klassekode: String) : Stønadstype {
    TILSYN_BARN_ENSLIG_FORSØRGER("TSTBASISP2-OP"),
    TILSYN_BARN_AAP("TSTBASISP4-OP"),
    TILSYN_BARN_ETTERLATTE("TSTBASISP5-OP"),
    LÆREMIDLER_ENSLIG_FORSØRGER("TSLMASISP2-OP"),
    LÆREMIDLER_AAP("TSLMASISP3-OP"),
    LÆREMIDLER_ETTERLATTE("TSLMASISP4-OP"),
}

enum class StønadTypeAAP(override val klassekode: String) : Stønadstype {
    AAP_UNDER_ARBEIDSAVKLARING("AAPUAA"),
}

enum class StønadTypeHistorisk(override val klassekode: String) : Stønadstype {
    TILSKUDD_SMÅHJELPEMIDLER("HJRIM"),
}

fun List<Utbetalingsperiode>.aggreger(periodetype: Periodetype): List<Utbetalingsperiode> {
    return sortedBy { it.fom }
        .groupBy { listOf(it.beløp, it.betalendeEnhet, it.vedtakssats) }
        .map { (_, perioder) ->
            perioder.splitWhen { a, b ->
                when (periodetype) {
                    Periodetype.UKEDAG -> !a.tom.nesteUkedag().equals(b.fom)
                    else -> !a.tom.plusDays(1).equals(b.fom)
                }
            }.map {
                Utbetalingsperiode(
                    fom = it.first().fom,
                    tom = it.last().tom,
                    beløp = beløp(it, periodetype),
                    betalendeEnhet = it.last().betalendeEnhet,
                    vedtakssats = it.last().vedtakssats,
                )
            }
        }.flatten()
}

private fun beløp(perioder: List<Utbetalingsperiode>, periodetype: Periodetype): UInt =
    when (periodetype) {
        Periodetype.DAG, Periodetype.UKEDAG, Periodetype.MND -> perioder.map { it.beløp }.toSet().singleOrNull() ?: 
            badRequest("fant fler ulike beløp blant dagene", "opprett_en_utbetaling")
        else -> perioder.singleOrNull()?.beløp ?: 
            badRequest("forventet kun en periode, da sammenslåing av beløp ikke er støttet", "opprett_en_utbetaling")
    }

fun uuid(
    sakId: SakId,
    fagsystem: Fagsystem,
    meldeperiode: String,
    stønad: Stønadstype,
): UUID {
    val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
    buffer.putLong((fagsystem.name + sakId.id + meldeperiode + stønad.klassekode).hashCode().toLong())

    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(buffer.array())

    val bb = ByteBuffer.wrap(hash)
    val mostSigBits = bb.long
    val leastSigBits = bb.long

    return UUID(mostSigBits, leastSigBits)
}

fun fakeDelete(
    originalKey: String,
    sakId: SakId,
    uid: UtbetalingId,
    fagsystem: Fagsystem,
    stønad: Stønadstype,
    beslutterId: Navident,
    saksbehandlerId: Navident,
) = Utbetaling(
    dryrun = false,
    originalKey = originalKey,
    fagsystem = fagsystem,
    uid = uid,
    action = Action.DELETE,
    førsteUtbetalingPåSak = false,
    sakId = sakId,
    behandlingId = BehandlingId(""),
    lastPeriodeId = PeriodeId(),
    personident = Personident(""),
    vedtakstidspunkt = LocalDateTime.now(),
    stønad = stønad,
    beslutterId = beslutterId,
    saksbehandlerId = saksbehandlerId,
    periodetype = Periodetype.UKEDAG,
    avvent = null,
    perioder = listOf(Utbetalingsperiode(LocalDate.now(), LocalDate.now(), 1u)), // placeholder
)
