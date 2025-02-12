package abetal.models

import com.fasterxml.jackson.annotation.JsonCreator
import abetal.*
import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import java.time.*
import kotlin.getOrThrow

@JvmInline
value class SakId(val id: String)

@JvmInline
value class BehandlingId(val id: String)

@JvmInline
value class NavEnhet(val enhet: String)

@JvmInline
value class Personident(val ident: String)

@JvmInline
value class Navident(val ident: String)

@JvmInline
value class UtbetalingId(val id: UUID) 

data class Utbetaling(
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
    fun validate() {
        failOnÅrsskifte()
        failOnDuplicatePerioder()
        failOnTomBeforeFom()
        failOnIllegalUseOfFastsattDagsats()
        failOnInconsistentPeriodeType()
        failOnIllegalFutureUtbetaling()
        failOnTooLongPeriods()
        // validate beløp
        // validate fom/tom
        // validate stønadstype opp mot e.g. fastsattDagsats
        // validate sakId ikke er for lang
    }
}

private fun Utbetaling.failOnÅrsskifte() {
    if (perioder.minBy { it.fom }.fom.year != perioder.maxBy { it.tom }.tom.year) {
        badRequest(
            msg = "periode strekker seg over årsskifte",
            field = "tom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
}

private fun Utbetaling.failOnDuplicatePerioder() {
    if (perioder.groupBy { it.fom }.any { (_, perioder) -> perioder.size != 1 }) {
        badRequest(
            msg = "kan ikke sende inn duplikate perioder",
            field = "fom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
    if (perioder.groupBy { it.tom }.any { (_, perioder) -> perioder.size != 1 }) {
        badRequest(
            msg = "kan ikke sende inn duplikate perioder",
            field = "tom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
}

private fun Utbetaling.failOnTomBeforeFom() {
    if (!perioder.all { it.fom <= it.tom }) {
        badRequest(
            msg = "fom må være før eller lik tom",
            field = "fom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
}

private fun Utbetaling.failOnIllegalUseOfFastsattDagsats() {
    when (stønad) {
        is StønadTypeDagpenger -> {}
        is StønadTypeAAP -> {}
        else -> {
            if (perioder.any { it.fastsattDagsats != null }) {
                badRequest(
                    msg = "reservert felt for Dagpenger og AAP",
                    field = "fastsattDagsats",
                    doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
                )
            }
        }
    }
}

private fun Utbetaling.failOnInconsistentPeriodeType() {
    fun LocalDate.erHelg(): Boolean {
        return dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }

    val consistent = when (periodetype) {
        Periodetype.UKEDAG -> perioder.all { it.fom == it.tom } && perioder.none { it.fom.erHelg() }
        Periodetype.DAG -> perioder.all { it.fom == it.tom }
        Periodetype.MND -> perioder.all { it.fom.dayOfMonth == 1 && it.tom.plusDays(1) == it.fom.plusMonths(1) }
        Periodetype.EN_GANG -> perioder.all { it.fom.year == it.tom.year } // tillater engangs over årsskifte
    }
    if (!consistent) {
        badRequest(
            msg = "inkonsistens blant datoene i periodene",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
}

private fun Utbetaling.failOnIllegalFutureUtbetaling() {
    if (stønad is StønadTypeTilleggsstønader) return
    if (periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG) && perioder.maxBy{ it.tom }.tom.isAfter(LocalDate.now())) {
        badRequest(
            msg = "fremtidige utbetalinger er ikke støttet for periode dag/ukedag",
            field = "periode.tom",
            doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
        )
    }
}

private fun Utbetaling.failOnTooLongPeriods() {
    if (periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG)) {
        val min = perioder.minBy { it.fom }.fom
        val max = perioder.maxBy { it.tom }.tom
        if (java.time.temporal.ChronoUnit.DAYS.between(min, max)+1 > 92) {
            badRequest(
                msg = "$periodetype støtter maks periode på 92 dager",
                field = "perioder",
                doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
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
    val betalendeEnhet: NavEnhet? = null,
    val fastsattDagsats: UInt? = null,
) 

fun List<Utbetalingsperiode>.betalendeEnhet(): NavEnhet? {
    return sortedBy { it.tom }.find { it.betalendeEnhet != null }?.betalendeEnhet
}

enum class Periodetype {
    DAG,
    UKEDAG, // TODO: rename, skal disse hete det samme som PeriodeType?
    MND,
    EN_GANG;
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

