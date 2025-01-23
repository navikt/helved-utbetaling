package utsjekk.utbetaling

import com.fasterxml.jackson.annotation.JsonCreator
import utsjekk.avstemming.erHelg
import utsjekk.badRequest
import utsjekk.conflict
import utsjekk.appLog
import libs.utils.logger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import kotlin.getOrThrow

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

data class Utbetaling(
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val lastPeriodeId: PeriodeId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: Stønadstype,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    val perioder: List<Utbetalingsperiode>,
) {
    companion object {
        fun from(dto: UtbetalingApi, lastPeriodeId: PeriodeId): Utbetaling =
            Utbetaling(
                sakId = SakId(dto.sakId),
                behandlingId = BehandlingId(dto.behandlingId),
                lastPeriodeId = lastPeriodeId,
                personident = Personident(dto.personident),
                vedtakstidspunkt = dto.vedtakstidspunkt,
                stønad = dto.stønad,
                beslutterId = Navident(dto.beslutterId),
                saksbehandlerId = Navident(dto.saksbehandlerId),
                perioder = listOf(Utbetalingsperiode.from(dto.perioder.sortedBy { it.fom })),
            )
    }

    fun validateLockedFields(other: Utbetaling) {
        if (sakId != other.sakId) badRequest("cant change immutable field", "sakId")
        if (personident != other.personident) badRequest(
            "cant change immutable field",
            "personident"
        ) // TODO: oppslag mot PDL, se at det fortsatt er samme person, ikke nødvendigvis samme ident
        if (stønad != other.stønad) badRequest("cant change immutable field", "stønad")

        val gyldigSatstype = perioder.first().satstype
        if (other.perioder.any { it.satstype != gyldigSatstype }) {
            badRequest(
                msg = "can't change the flavour of perioder",
                field = "perioder",
                doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder",
            )
        }
        // validateDiff(perioder, other.perioder)
    }

    fun validateMinimumChanges(other: Utbetaling) {
        val ingenEndring = perioder.zip(other.perioder).all { (first, second) ->
            first.beløp == second.beløp
                    && first.fom == second.fom
                    && first.tom == second.tom
                    && first.satstype == second.satstype
                    && first.betalendeEnhet == second.betalendeEnhet
                    && first.fastsattDagpengesats == second.fastsattDagpengesats
        }
        if (ingenEndring) {
            conflict(
                msg = "periods allready exists",
                field = "perioder",
                doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder",
            )
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
    val satstype: Satstype,
    val betalendeEnhet: NavEnhet? = null,
    val fastsattDagpengesats: UInt? = null,
) {
    companion object {
        fun from(perioder: List<UtbetalingsperiodeApi>): Utbetalingsperiode {
            val satstype = satstype(perioder.map { satstype(it.fom, it.tom) }) // may throw bad request

            return Utbetalingsperiode(
                fom = perioder.first().fom,
                tom = perioder.last().tom,
                beløp = beløp(perioder, satstype),
                betalendeEnhet = perioder.last().betalendeEnhet ?.let(::NavEnhet), // baserer oss på lastest news
                fastsattDagpengesats = perioder.last().fastsattDagpengesats, // baserer oss på lastest news
                satstype = satstype,
            )
        }
    }
}

fun List<Utbetalingsperiode>.betalendeEnhet(): NavEnhet? {
    return sortedBy { it.tom }.find { it.betalendeEnhet != null }?.betalendeEnhet
}

enum class Satstype {
    /** Alle dager, man-søn */
    DAG,

    /** hverdager, man-fre */
    VIRKEDAG,
    MND,
    ENGANGS
}

data class UtbetalingStatus(
    val status: Status,
)

enum class Status {
    SENDT_TIL_OPPDRAG,
    FEILET_MOT_OPPDRAG,
    OK,
    IKKE_PÅBEGYNT,
    OK_UTEN_UTBETALING,
}

sealed interface Stønadstype {
    val name: String

    companion object {
        @JsonCreator
        @JvmStatic
        fun valueOf(str: String): Stønadstype =
            runCatching { StønadTypeDagpenger.valueOf(str) }
                .recoverCatching { StønadTypeTilleggsstønader.valueOf(str) }
                .recoverCatching { StønadTypeTiltakspenger.valueOf(str) }
                .getOrThrow()
    }

    fun asFagsystemStr() =
        when (this) {
            is StønadTypeDagpenger -> "DAGPENGER"
            is StønadTypeTiltakspenger -> "TILTAKSPENGER"
            is StønadTypeTilleggsstønader -> "TILLEGGSSTØNADER"
        }
}

enum class StønadTypeDagpenger : Stønadstype {
    ARBEIDSSØKER_ORDINÆR,
    PERMITTERING_ORDINÆR,
    PERMITTERING_FISKEINDUSTRI,
    EØS,
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG,
    PERMITTERING_ORDINÆR_FERIETILLEGG,
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG,
    EØS_FERIETILLEGG,
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD,
    PERMITTERING_ORDINÆR_FERIETILLEGG_AVDØD,
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG_AVDØD,
}

// TODO: legg til klassekodene for barnetillegg
enum class StønadTypeTiltakspenger : Stønadstype {
    ARBEIDSFORBEREDENDE_TRENING,
    ARBEIDSRETTET_REHABILITERING,
    ARBEIDSTRENING,
    AVKLARING,
    DIGITAL_JOBBKLUBB,
    ENKELTPLASS_AMO,
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG,
    FORSØK_OPPLÆRING_LENGRE_VARIGHET,
    GRUPPE_AMO,
    GRUPPE_VGS_OG_HØYERE_YRKESFAG,
    HØYERE_UTDANNING,
    INDIVIDUELL_JOBBSTØTTE,
    INDIVIDUELL_KARRIERESTØTTE_UNG,
    JOBBKLUBB,
    OPPFØLGING,
    UTVIDET_OPPFØLGING_I_NAV,
    UTVIDET_OPPFØLGING_I_OPPLÆRING,
    ARBEIDSFORBEREDENDE_TRENING_BARN,
    ARBEIDSRETTET_REHABILITERING_BARN,
    ARBEIDSTRENING_BARN,
    AVKLARING_BARN,
    DIGITAL_JOBBKLUBB_BARN,
    ENKELTPLASS_AMO_BARN,
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN,
    FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN,
    GRUPPE_AMO_BARN,
    GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN,
    HØYERE_UTDANNING_BARN,
    INDIVIDUELL_JOBBSTØTTE_BARN,
    INDIVIDUELL_KARRIERESTØTTE_UNG_BARN,
    JOBBKLUBB_BARN,
    OPPFØLGING_BARN,
    UTVIDET_OPPFØLGING_I_NAV_BARN,
    UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN,
}

enum class StønadTypeTilleggsstønader : Stønadstype {
    TILSYN_BARN_ENSLIG_FORSØRGER,
    TILSYN_BARN_AAP,
    TILSYN_BARN_ETTERLATTE,
    LÆREMIDLER_ENSLIG_FORSØRGER,
    LÆREMIDLER_AAP,
    LÆREMIDLER_ETTERLATTE,
}

private fun satstype(fom: LocalDate, tom: LocalDate): Satstype =
    when {
        fom.dayOfMonth == 1 && tom.plusDays(1) == fom.plusMonths(1) -> Satstype.MND
        fom == tom -> if (fom.erHelg()) Satstype.DAG else Satstype.VIRKEDAG
        else -> Satstype.ENGANGS
    }

private fun satstype(satstyper: List<Satstype>): Satstype {
    if (satstyper.size == 1 && satstyper.none { it == Satstype.MND }) {
        return Satstype.ENGANGS
    }
    if (satstyper.all { it == Satstype.VIRKEDAG }) {
        return Satstype.VIRKEDAG
    }
    if (satstyper.all { it == Satstype.MND }) {
        return Satstype.MND
    }
    if (satstyper.any { it == Satstype.DAG }) {
        return Satstype.DAG
    }

    badRequest(
        msg = "inkonsistens blant datoene i periodene.",
        doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
    )
}

private fun beløp(perioder: List<UtbetalingsperiodeApi>, satstype: Satstype): UInt =
    when (satstype) {
        Satstype.DAG, Satstype.VIRKEDAG, Satstype.MND ->
            perioder.map { it.beløp }.toSet().singleOrNull()
                ?: badRequest(
                    msg = "fant fler ulike beløp blant dagene",
                    field = "beløp",
                    doc =
                        "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
                )

        else -> perioder.singleOrNull()?.beløp
            ?: badRequest(
                msg =
                    "forventet kun en periode, da sammenslåing av beløp ikke er støttet",
                field = "beløp",
                doc =
                    "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
            )
    }
