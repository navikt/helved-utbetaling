package utsjekk.utbetaling

import com.fasterxml.jackson.annotation.JsonCreator
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import libs.utils.*
import models.DocumentedErrors
import models.badRequest
import models.conflict
import models.splitWhen
import java.time.DayOfWeek
import java.time.MonthDay

@JvmInline
value class SakId(val id: String)

@JvmInline
value class BehandlingId(val id: String)

@JvmInline
value class NavEnhet(val enhet: String)

@JvmInline
value class Personident(val ident: String) {
    companion object
}

@JvmInline
value class Navident(val ident: String) {
    companion object
}

@JvmInline
value class UtbetalingId(val id: UUID) {
    companion object
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

data class Utbetaling(
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val lastPeriodeId: PeriodeId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: Stønadstype,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    val satstype: Satstype,
    val perioder: List<Utbetalingsperiode>,
    val avvent: Avvent?,
    val erFørsteUtbetaling: Boolean? = null,
) {
    companion object {
        fun from(dto: UtbetalingApi, lastPeriodeId: PeriodeId = PeriodeId()): Utbetaling =
            Utbetaling(
                sakId = SakId(dto.sakId),
                behandlingId = BehandlingId(dto.behandlingId),
                lastPeriodeId = lastPeriodeId,
                personident = Personident(dto.personident),
                erFørsteUtbetaling = dto.erFørsteUtbetaling,
                vedtakstidspunkt = dto.vedtakstidspunkt,
                stønad = dto.stønad,
                beslutterId = Navident(dto.beslutterId),
                saksbehandlerId = Navident(dto.saksbehandlerId),
                satstype = Satstype.from(dto.periodeType),
                avvent = dto.avvent,
                perioder = dto.perioder
                    .sortedBy { it.fom }
                    .groupBy { listOf(it.beløp, it.betalendeEnhet, it.fastsattDagsats) }
                    .map { (_, perioder) ->
                        perioder.splitWhen { a, b ->
                            when (dto.periodeType) {
                                PeriodeType.UKEDAG -> a.tom.nesteUkedag() != b.fom
                                else -> a.tom.plusDays(1) != b.fom
                            }
                        }
                            .map { Utbetalingsperiode.from(it, Satstype.from(dto.periodeType)) }
                    }
                    .flatten()
                    .sortedBy { it.fom },
            )
    }

    fun validateLockedFields(other: Utbetaling) {
        if (sakId != other.sakId) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_SAK_ID)
        // TODO: oppslag mot PDL, se at det fortsatt er samme person, ikke nødvendigvis samme ident
        if (personident != other.personident) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_PERSONIDENT)
        if (stønad != other.stønad) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_STØNAD)

        if (satstype != other.satstype) {
            badRequest("Kan ikke ha forskjellige satstyper")
        }
    }

    fun validateEqualityOnDelete(other: Utbetaling) {
        if (perioder != other.perioder) {
            badRequest("Periodene i utbetalingen samsvarer ikke med det som er lagret hos utsjekk.")
        }
    }

    fun validateMinimumChanges(other: Utbetaling) {
        if (perioder.size != other.perioder.size) {
            return
        }
        val ingenEndring = perioder.zip(other.perioder).all { (first, second) ->
            first.beløp == second.beløp
                    && first.fom == second.fom
                    && first.tom == second.tom
                    && first.betalendeEnhet == second.betalendeEnhet
                    && first.fastsattDagsats == second.fastsattDagsats
        }
        if (ingenEndring) {
            conflict(DocumentedErrors.Async.Utbetaling.MINIMUM_CHANGES)
        }
    }
}

@JvmInline
value class PeriodeId(private val id: UUID) {
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
                appLog.debug("Klarte ikke dekomprimere UUID: $this")
                secureLog.warn("Klarte ikke dekomprimere UUID: $this.", e)
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
) {
    companion object {
        fun from(perioder: List<UtbetalingsperiodeApi>, satstype: Satstype): Utbetalingsperiode {
            return Utbetalingsperiode(
                fom = perioder.first().fom,
                tom = perioder.last().tom,
                beløp = beløp(perioder, satstype),
                betalendeEnhet = perioder.last().betalendeEnhet?.let(::NavEnhet), // Det er OK å basere seg på siste betalendeEnhet
                fastsattDagsats = perioder.last().fastsattDagsats, // baserer oss på lastest news
            )
        }
    }
}

fun List<Utbetalingsperiode>.betalendeEnhet(): NavEnhet? {
    return sortedBy { it.tom }.find { it.betalendeEnhet != null }?.betalendeEnhet
}

enum class Satstype(val kode: String) {
    DAG("DAG7"),
    VIRKEDAG("DAG"), // TODO: rename, skal disse hete det samme som PeriodeType?
    MND("MND"),
    ENGANGS("ENG");

    companion object {
        fun from(type: PeriodeType) = when (type) {
            PeriodeType.UKEDAG -> VIRKEDAG
            PeriodeType.DAG -> DAG
            PeriodeType.MND -> MND
            PeriodeType.EN_GANG -> ENGANGS
        }
    }
}

enum class Status {
    SENDT_TIL_OPPDRAG,
    FEILET_MOT_OPPDRAG,
    OK,
    IKKE_PÅBEGYNT,
    OK_UTEN_UTBETALING,
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
                .recoverCatching { StønadTypeHistorisk.valueOf(str) }
                .getOrThrow()

        fun fraKode(klassekode: String): Stønadstype =
            (StønadTypeDagpenger.entries + StønadTypeTiltakspenger.entries + StønadTypeTilleggsstønader.entries +
                    StønadTypeAAP.entries + StønadTypeHistorisk.entries)
                .single { it.klassekode == klassekode }
    }

    fun asFagsystemStr() =
        when (this) {
            is StønadTypeDagpenger -> "DAGPENGER"
            is StønadTypeTiltakspenger -> "TILTAKSPENGER"
            is StønadTypeTilleggsstønader -> "TILLEGGSSTØNADER"
            is StønadTypeAAP -> "AAP"
            is StønadTypeHistorisk -> "HISTORISK"
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
    AAP_UNDER_ARBEIDSAVKLARING("AAPOR"),
}

enum class StønadTypeHistorisk(override val klassekode: String) : Stønadstype {
    TILSKUDD_SMÅHJELPEMIDLER("HJRIM"),
}

private fun beløp(perioder: List<UtbetalingsperiodeApi>, satstype: Satstype): UInt =
    when (satstype) {
        Satstype.DAG, Satstype.VIRKEDAG, Satstype.MND ->
            perioder.map { it.beløp }.toSet().singleOrNull()
                ?: badRequest("Fant fler ulike beløp blant dagene")

        else -> perioder.singleOrNull()?.beløp
            ?: badRequest("Forventet kun en periode, da sammenslåing av beløp ikke er støttet")
    }

fun LocalDate.nesteVirkedag(): LocalDate {
    var nesteDag = this.plusDays(1)

    while (nesteDag.erHelligdag()) {
        nesteDag = nesteDag.plusDays(1)
    }
    return nesteDag
}

fun LocalDate.nesteUkedag(): LocalDate {
    var nesteDag = this.plusDays(1)

    while (nesteDag.erHelg()) {
        nesteDag = nesteDag.plusDays(1)
    }
    return nesteDag
}

fun LocalDate.erHelligdag(): Boolean {
    val helligDager = FASTE_HELLIGDAGER + beregnBevegeligeHelligdager(year)
    val erHelligdag = helligDager.contains(MonthDay.from(this))
    return erHelg() || erHelligdag
}

fun LocalDate.erHelg(): Boolean {
    return dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}

private val FASTE_HELLIGDAGER = setOf(
    MonthDay.of(1, 1),
    MonthDay.of(5, 1),
    MonthDay.of(5, 17),
    MonthDay.of(12, 25),
    MonthDay.of(12, 26),
)

private fun beregnBevegeligeHelligdager(år: Int): Set<MonthDay> {
    val påskedag = utledPåskedag(år)
    val skjærTorsdag = påskedag.minusDays(3)
    val langfredag = påskedag.minusDays(2)
    val andrePåskedag = påskedag.plusDays(1)
    val kristiHimmelfartsdag = påskedag.plusDays(39)
    val førstePinsedag = påskedag.plusDays(49)
    val andrePinsedag = påskedag.plusDays(50)

    return setOf(
        MonthDay.from(påskedag),
        MonthDay.from(skjærTorsdag),
        MonthDay.from(langfredag),
        MonthDay.from(andrePåskedag),
        MonthDay.from(kristiHimmelfartsdag),
        MonthDay.from(førstePinsedag),
        MonthDay.from(andrePinsedag),
    )
}

// Butcher-Meeus algoritm
private fun utledPåskedag(år: Int): LocalDate {
    val a = år % 19
    val b = år / 100
    val c = år % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val n = (h + l - 7 * m + 114) / 31
    val p = (h + l - 7 * m + 114) % 31
    return LocalDate.of(år, n, p + 1)
}
