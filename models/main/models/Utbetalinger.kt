package models

import com.fasterxml.jackson.annotation.JsonCreator
import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import java.time.*
import kotlin.getOrThrow
import libs.utils.secureLog

@JvmInline value class SakId(val id: String)
@JvmInline value class BehandlingId(val id: String)
@JvmInline value class NavEnhet(val enhet: String)
@JvmInline value class Personident(val ident: String)
@JvmInline value class Navident(val ident: String)
@JvmInline value class UtbetalingId(val id: UUID)

data class Utbetaling(
    val simulate: Boolean,
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
    val perioder: List<Utbetalingsperiode>,
) {
    fun validate(prev: Utbetaling?) {
        failOnÅrsskifte()
        failOnDuplicatePerioder()
        failOnTomBeforeFom()
        failOnInconsistentPeriodeType()
        failOnIllegalFutureUtbetaling()
        failOnTooManyPeriods()
        failOnDuplicate(prev)
    }
}

fun Utbetaling.validateLockedFields(other: Utbetaling) {
    if (sakId != other.sakId) badRequest("cant change immutable field 'sakId'")
    if (personident != other.personident) badRequest("cant change immutable field 'personident'")
    if (stønad != other.stønad) badRequest("cant change immutable field")
    if (periodetype != other.periodetype) badRequest("can't change periodetype", "/utbetalinger/perioder")
}

fun Utbetaling.validateMinimumChanges(other: Utbetaling) {
    if (perioder.size != other.perioder.size) return
    val ingenEndring = perioder.zip(other.perioder).all { (l, r) -> l.beløp == r.beløp && l.fom == r.fom && l.tom == r.tom && l.betalendeEnhet == r.betalendeEnhet && l.fastsattDagsats == r.fastsattDagsats }
    if (ingenEndring) conflict("periods allready exists", "/utbetalinger/perioder")
}

private fun Utbetaling.failOnÅrsskifte() {
    if (perioder.minBy { it.fom }.fom.year != perioder.maxBy { it.tom }.tom.year) {
        badRequest("periode strekker seg over årsskifte", "/utbetalinger/perioder")
    }
}

private fun Utbetaling.failOnDuplicatePerioder() {
    val dupFom = perioder.groupBy { it.fom }.any { (_, perioder) -> perioder.size != 1 }
    if (dupFom) badRequest("kan ikke sende inn duplikate perioder", "/utbetalinger/perioder")
    val dupTom = perioder.groupBy { it.tom }.any { (_, perioder) -> perioder.size != 1 }
    if (dupTom) badRequest("kan ikke sende inn duplikate perioder", "/utbetalinger/perioder")
}

private fun Utbetaling.failOnTomBeforeFom() {
    if (!perioder.all { it.fom <= it.tom }) badRequest("fom må være før eller lik tom", "/utbetalinger/perioder")
}

private fun Utbetaling.failOnInconsistentPeriodeType() {
    fun LocalDate.erHelg() = dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val consistent = when (periodetype) {
        Periodetype.UKEDAG -> perioder.all { it.fom == it.tom } && perioder.none { it.fom.erHelg() }
        Periodetype.DAG -> perioder.all { it.fom == it.tom }
        Periodetype.MND -> perioder.all { it.fom.dayOfMonth == 1 && it.tom.plusDays(1) == it.fom.plusMonths(1) }
        Periodetype.EN_GANG -> perioder.all { it.fom.year == it.tom.year } // tillater engangs over årsskifte
    }
    if (!consistent) badRequest("inkonsistens blant datoene i periodene")
}

// TODO: remove?
private fun Utbetaling.failOnIllegalFutureUtbetaling() {
    if (stønad is StønadTypeTilleggsstønader) return
    val isDay = periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG) 
    val dayIsFuture = perioder.maxBy{ it.tom }.tom.isAfter(LocalDate.now()) 
    if (isDay && dayIsFuture) badRequest(
        "fremtidige utbetalinger er ikke støttet for periode dag/ukedag",
        "/utbetalinger/perioder"
    )
}

// TODO: remove?
private fun Utbetaling.failOnTooManyPeriods() {
    if (periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG)) {
        val min = perioder.minBy { it.fom }.fom
        val max = perioder.maxBy { it.tom }.tom
        val tooManyPeriods = java.time.temporal.ChronoUnit.DAYS.between(min, max)+1 > 92 
        if (tooManyPeriods) badRequest("$periodetype støtter maks periode på 92 dager", "/utbetalinger/perioder")
    }
}

private fun Utbetaling.failOnDuplicate(prev: Utbetaling?) {
    prev?.let {
        if (this.copy(førsteUtbetalingPåSak = prev.førsteUtbetalingPåSak) == prev){
            conflict("Denne meldingen har du allerede sendt inn")
        } 
    }
}

@JvmInline
value class PeriodeId (private val id: UUID) {
    constructor() : this(UUID.randomUUID())

    init { 
        toString() // ikke bruk en periodeId som ikke lar seg sendes over SOAP
    }
    companion object {
        fun decode(encoded: String): PeriodeId {
            try {
                val byteBuffer: ByteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded))
                // ||||||||||||||||||||||||||||||||||||||||||||||||||||||||
                // ^ les neste 64 og lag en long
                return PeriodeId(UUID(byteBuffer.long, byteBuffer.long))
            } catch (e: Throwable) {
                secureLog.warn("Klarte ikke dekomprimere UUID: $this")
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
    val fastsattDagsats: UInt? = null,
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
    ARBEIDSSØKER_ORDINÆR("DPORAS"),
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG("DPORASFE"),
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD("DPORASFE-IOP"),
    PERMITTERING_ORDINÆR("DPPEASFE1"),
    PERMITTERING_ORDINÆR_FERIETILLEGG("DPPEAS"),
    PERMITTERING_ORDINÆR_FERIETILLEGG_AVDØD("DPPEASFE1-IOP"),
    PERMITTERING_FISKEINDUSTRI("DPPEFIFE1"),
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG("DPPEFI"),
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG_AVDØD("DPPEFIFE1-IOP"),
    EØS("DPFEASISP"),
    EØS_FERIETILLEGG("DPDPASISP1");
}

// TODO: legg til klassekodene for barnetillegg
enum class StønadTypeTiltakspenger(override val klassekode: String) : Stønadstype {
    ARBEIDSFORBEREDENDE_TRENING("TPBTAF"),
    ARBEIDSRETTET_REHABILITERING("TPBTARREHABAGDAG"),
    ARBEIDSTRENING("TPBTATTILT"),
    AVKLARING("TPBTAAGR"),
    DIGITAL_JOBBKLUBB("TPBTDJK"),
    ENKELTPLASS_AMO("TPBTEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG("TPBTEPVGSHOY"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET("TPBTFLV"),
    GRUPPE_AMO("TPBTGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG("TPBTGRVGSHOY"),
    HØYERE_UTDANNING("TPBTHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE("TPBTIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG("TPBTIPSUNG"),
    JOBBKLUBB("TPBTJK2009"),
    OPPFØLGING("TPBTOPPFAGR"),
    UTVIDET_OPPFØLGING_I_NAV("TPBTUAOPPFL"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING("TPBTUOPPFOPPL"),
    ARBEIDSFORBEREDENDE_TRENING_BARN("TPTPAFT"),
    ARBEIDSRETTET_REHABILITERING_BARN("TPTPARREHABAGDAG"),
    ARBEIDSTRENING_BARN("TPTPATT"),
    AVKLARING_BARN("TPTPAAG"),
    DIGITAL_JOBBKLUBB_BARN("TPTPDJB"),
    ENKELTPLASS_AMO_BARN("TPTPEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN("TPTPEPVGSHOU"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN("TPTPFLV"),
    GRUPPE_AMO_BARN("TPTPGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN("TPTPGRVGSHOY"),
    HØYERE_UTDANNING_BARN("TPTPHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE_BARN("TPTPIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG_BARN("TPTPIPSUNG"),
    JOBBKLUBB_BARN("TPTPJK2009"),
    OPPFØLGING_BARN("TPTPOPPFAG"),
    UTVIDET_OPPFØLGING_I_NAV_BARN("TPTPUAOPPF"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN("TPTPUOPPFOPPL"),
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

fun LocalDate.nesteUkedag(): LocalDate {
    var nesteDag = this.plusDays(1)
    while (nesteDag.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
        nesteDag = nesteDag.plusDays(1)
    }
    return nesteDag
}

fun List<Utbetalingsperiode>.aggreger(periodetype: Periodetype): List<Utbetalingsperiode> {
    return sortedBy { it.fom }
        .groupBy { listOf(it.beløp, it.betalendeEnhet, it.fastsattDagsats) }
        .map { (_, perioder) ->
            perioder.splitWhen { a, b ->
                when (periodetype) {
                    Periodetype.UKEDAG -> a.tom.nesteUkedag() != b.fom
                    else -> a.tom.plusDays(1) != b.fom
                }
            }.map {
                Utbetalingsperiode(
                    fom = it.first().fom,
                    tom = it.last().tom,
                    beløp = beløp(it, periodetype),
                    betalendeEnhet = it.last().betalendeEnhet,
                    fastsattDagsats = it.last().fastsattDagsats,
                )
            }
        }.flatten()
}

private fun beløp(perioder: List<Utbetalingsperiode>, periodetype: Periodetype): UInt =
    when (periodetype) {
        Periodetype.DAG, Periodetype.UKEDAG, Periodetype.MND -> perioder.map { it.beløp }.toSet().singleOrNull() ?: 
            badRequest("fant fler ulike beløp blant dagene", "/utbetalinger/perioder")
        else -> perioder.singleOrNull()?.beløp ?: 
            badRequest("forventet kun en periode, da sammenslåing av beløp ikke er støttet", "/utbetalinger/perioder")
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

