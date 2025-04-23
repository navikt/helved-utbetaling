package utsjekk.utbetaling

import com.fasterxml.jackson.annotation.JsonCreator
import utsjekk.*
import utsjekk.avstemming.nesteUkedag
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

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
    val overføres: LocalDate,
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
) {
    companion object {
        fun from(dto: UtbetalingApi, lastPeriodeId: PeriodeId = PeriodeId()): Utbetaling =
            Utbetaling(
                sakId = SakId(dto.sakId),
                behandlingId = BehandlingId(dto.behandlingId),
                lastPeriodeId = lastPeriodeId,
                personident = Personident(dto.personident),
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
                    }.flatten()
            )
    }

    fun validateLockedFields(other: Utbetaling) {
        if (sakId != other.sakId) badRequest("cant change immutable field", "sakId")
        // TODO: oppslag mot PDL, se at det fortsatt er samme person, ikke nødvendigvis samme ident
        if (personident != other.personident) badRequest("cant change immutable field", "personident")
        if (stønad != other.stønad) badRequest("cant change immutable field", "stønad")

        if (satstype != other.satstype) {
            badRequest(
                msg = "can't change the flavour of perioder",
                field = "perioder",
                doc = "opprett_en_utbetaling",
            )
        }
    }

    fun validateEqualityOnDelete(other: Utbetaling) {
        if (perioder != other.perioder) {
            badRequest(
                msg = "periodene i utbetalingen samsvarer ikke med det som er lagret hos utsjekk.",
                field = "utbetaling",
                doc = "opphor_en_utbetaling",
            )
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
            conflict(
                msg = "periods allready exists",
                field = "perioder",
                doc = "opprett_en_utbetaling",
            )
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
                appLog.warn("Klarte ikke dekomprimere UUID: $this")
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
                .getOrThrow()

        fun fraKode(klassekode: String): Stønadstype =
            (StønadTypeDagpenger.entries + StønadTypeTiltakspenger.entries + StønadTypeTilleggsstønader.entries + StønadTypeAAP.entries)
                .single { it.klassekode == klassekode }
    }

    fun asFagsystemStr() =
        when (this) {
            is StønadTypeDagpenger -> "DAGPENGER"
            is StønadTypeTiltakspenger -> "TILTAKSPENGER"
            is StønadTypeTilleggsstønader -> "TILLEGGSSTØNADER"
            is StønadTypeAAP -> "AAP"
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


// private fun satstype(fom: LocalDate, tom: LocalDate): Satstype =
//     when {
//         fom.dayOfMonth == 1 && tom.plusDays(1) == fom.plusMonths(1) -> Satstype.MND
//         fom == tom -> if (fom.erHelg()) Satstype.DAG else Satstype.VIRKEDAG
//         else -> Satstype.ENGANGS
//     }

// private fun satstype(satstyper: List<Satstype>): Satstype {
//     if (satstyper.size == 1 && satstyper.none { it == Satstype.MND }) {
//         return Satstype.ENGANGS
//     }
//     if (satstyper.all { it == Satstype.VIRKEDAG }) {
//         return Satstype.VIRKEDAG
//     }
//     if (satstyper.all { it == Satstype.MND }) {
//         return Satstype.MND
//     }
//     if (satstyper.any { it == Satstype.DAG }) {
//         return Satstype.DAG
//     }
//
//     badRequest(
//         msg = "inkonsistens blant datoene i periodene.",
//         doc = "utbetalinger/perioder"
//     )
// }

private fun beløp(perioder: List<UtbetalingsperiodeApi>, satstype: Satstype): UInt =
    when (satstype) {
        Satstype.DAG, Satstype.VIRKEDAG, Satstype.MND ->
            perioder.map { it.beløp }.toSet().singleOrNull()
                ?: badRequest(
                    msg = "fant fler ulike beløp blant dagene",
                    field = "beløp",
                    doc =
                        "opprett_en_utbetaling"
                )

        else -> perioder.singleOrNull()?.beløp
            ?: badRequest(
                msg =
                    "forventet kun en periode, da sammenslåing av beløp ikke er støttet",
                field = "beløp",
                doc =
                    "${DEFAULT_DOC_STR}utbetalinger/perioder"
            )
    }

fun <T> List<T>.splitWhen(predicate: (T, T) -> Boolean): List<List<T>> {
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
