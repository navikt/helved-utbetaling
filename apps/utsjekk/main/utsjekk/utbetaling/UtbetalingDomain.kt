package utsjekk.utbetaling

import com.fasterxml.jackson.annotation.JsonCreator
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import libs.utils.*
import utsjekk.*
import utsjekk.avstemming.nesteUkedag

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

// Forslag til nytt domeneobjekt som representerer en utbetaling
data class UtbetalingV2(
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    // Hver liste med perioder pr. stønadstype vil kjedes sammen. Her kan man også endre hvordan ting kjedes ved å bruke
    // noe annet enn stønadstype som nøkkel, som f.eks. stønadstype + måned f.eks.
    val kjeder: Map<String, Utbetalingsperioder>,
    val avvent: Avvent?,
) {
    data class Utbetalingsperioder(
        val satstype: Satstype, // Perioder innen samme kjede kan ikke ha forskjellige satstyper
        val stønad: Stønadstype, // Perioder innen samme kjede kan ikke ha forskjellige stønadstyper
        val perioder: List<Utbetalingsperiode>,
        // IDen til siste periode i `perioder`. Brukes for å fortsette kjeden når vi genererer nye oppdragslinjer.
        val lastPeriodeId: String?
    )

    data class Utbetalingsperiode(
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: UInt,
        val betalendeEnhet: NavEnhet? = null,
        val vedtakssats: UInt? = null,
    )

    fun validateLockedFields(other: UtbetalingV2) {
        if (sakId != other.sakId) badRequest("cant change immutable field", "sakId")
        // TODO: oppslag mot PDL, se at det fortsatt er samme person, ikke nødvendigvis samme ident
        if (personident != other.personident) badRequest("cant change immutable field", "personident")
    }

    fun validateMinimumChanges(other: UtbetalingV2) {
        if (kjeder.keys != other.kjeder.keys) return // Har forskjellig antall kjeder
        if (kjeder.entries.any { other.kjeder[it.key]?.perioder?.size != it.value.perioder.size }) {
            return // Har forskjellig antall perioder i samme kjede
        }

        // Sammenligne periode for periode og se om det finnes endring
        val ingenEndring = kjeder.entries.all {
            it.value.perioder.zip(other.kjeder[it.key]!!.perioder).all { (first, second) ->
                first.beløp == second.beløp
                        && first.fom == second.fom
                        && first.tom == second.tom
                        && first.betalendeEnhet == second.betalendeEnhet
                        && first.vedtakssats == second.vedtakssats
            }
        }

        if (ingenEndring) {
            conflict(
                msg = "periods already exists",
                field = "perioder",
                doc = "opprett_en_utbetaling",
            )
        }
    }
}

fun UtbetalingV2.fagsystem(): FagsystemDto {
    return FagsystemDto.from(kjeder.values.first().stønad)
}

fun UtbetalingV2.betalendeEnhet(): NavEnhet? {
    return kjeder.values.flatMap { it.perioder }.sortedBy { it.tom }.find { it.betalendeEnhet != null }?.betalendeEnhet
}

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
                msg = "periods already exists",
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
