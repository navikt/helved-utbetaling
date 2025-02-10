package abetal.models

import com.fasterxml.jackson.annotation.JsonCreator
import abetal.*
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
    val satstype: Satstype,
    val perioder: List<Utbetalingsperiode>,
) 

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
    val betalendeEnhet: NavEnhet? = null,
    val fastsattDagsats: UInt? = null,
) 

fun List<Utbetalingsperiode>.betalendeEnhet(): NavEnhet? {
    return sortedBy { it.tom }.find { it.betalendeEnhet != null }?.betalendeEnhet
}

enum class Satstype {
    DAG,
    VIRKEDAG, // TODO: rename, skal disse hete det samme som PeriodeType?
    MND,
    ENGANGS;
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
                .recoverCatching { StønadTypeAAP.valueOf(str) }
                .getOrThrow()
    }

    fun asFagsystemStr() =
        when (this) {
            is StønadTypeDagpenger -> "DAGPENGER"
            is StønadTypeTiltakspenger -> "TILTAKSPENGER"
            is StønadTypeTilleggsstønader -> "TILLEGGSSTØNADER"
            is StønadTypeAAP -> "AAP"
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

enum class StønadTypeAAP: Stønadstype {
    AAP_UNDER_ARBEIDSAVKLARING,
}

