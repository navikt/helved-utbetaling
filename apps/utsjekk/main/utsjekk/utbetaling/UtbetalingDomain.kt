package utsjekk.utbetaling

import com.fasterxml.jackson.annotation.JsonCreator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import utsjekk.avstemming.erHelg
import utsjekk.badRequest

@JvmInline value class SakId(val id: String)

@JvmInline value class BehandlingId(val id: String)

@JvmInline value class NavEnhet(val enhet: String)

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
        val personident: Personident,
        val vedtakstidspunkt: LocalDateTime,
        val stønad: Stønadstype,
        val beslutterId: Navident,
        val saksbehandlerId: Navident,
        val periode: Utbetalingsperiode,
) {
    companion object {

        // vi skal ikke lage ny periodeId, men gjennbruke den fra databasen
        fun from(dto: UtbetalingApi): Utbetaling { 
            return from(dto, 0u)
        }

        fun from(dto: UtbetalingApi, periodeId: UInt): Utbetaling =
                Utbetaling(
                        sakId = SakId(dto.sakId),
                        behandlingId = BehandlingId(dto.behandlingId),
                        personident = Personident(dto.personident),
                        vedtakstidspunkt = dto.vedtakstidspunkt,
                        stønad = dto.stønad,
                        beslutterId = Navident(dto.beslutterId),
                        saksbehandlerId = Navident(dto.saksbehandlerId),
                        periode = Utbetalingsperiode.from(dto.perioder.sortedBy { it.fom }, periodeId),
                )
    }

    fun validateDiff(other: Utbetaling) {
        if (sakId != other.sakId) badRequest("cant change immutable field", "sakId")
        if (personident != other.personident) badRequest("cant change immutable field", "personident") // TODO: oppslag mot PDL, se at det fortsatt er samme person, ikke nødvendigvis samme ident
        if (stønad != other.stønad) badRequest("cant change immutable field", "stønad")
        periode.validateDiff(other.periode)
    }
}

data class Utbetalingsperiode(
        val id: UInt,
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: UInt,
        val satstype: Satstype,
        val betalendeEnhet: NavEnhet? = null,
        val fastsattDagpengesats: UInt? = null,
) {
    companion object {
        fun from(perioder: List<UtbetalingsperiodeApi>, periodeId: UInt): Utbetalingsperiode {
            val satstype = satstype(perioder.map { satstype(it.fom, it.tom) }) // may throw bad request

            return Utbetalingsperiode(
                    id = periodeId, 
                    fom = perioder.first().fom,
                    tom = perioder.last().tom,
                    beløp = beløp(perioder, satstype),
                    betalendeEnhet =
                            perioder.last()
                                    .betalendeEnhet
                                    ?.let(::NavEnhet), // baserer oss på lastest news
                    fastsattDagpengesats =
                            perioder.last().fastsattDagpengesats, // baserer oss på lastest news
                    satstype = satstype,
            )
        }
    }

    fun validateDiff(other: Utbetalingsperiode) {
        if (id != other.id) {
            error("periode.id for utbetaling må være lik ${id} != ${other.id}")
        }
        if (satstype != other.satstype) {
            badRequest(
                    msg = "cant change the flavour of perioder",
                    field = "perioder",
                    doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder",
            )
        }
    }
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
    EØS_FERIETILLEGG_AVDØD
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
}

enum class StønadTypeTilleggsstønader : Stønadstype {
    TILSYN_BARN_ENSLIG_FORSØRGER,
    TILSYN_BARN_AAP,
    TILSYN_BARN_ETTERLATTE,
    LÆREMIDLER_ENSLIG_FORSØRGER,
    LÆREMIDLER_AAP,
    LÆREMIDLER_ETTERLATTE,
    TILSYN_BARN_ENSLIG_FORSØRGER_BARNETILLEGG,
    TILSYN_BARN_AAP_BARNETILLEGG,
    TILSYN_BARN_ETTERLATTE_BARNETILLEGG,
    LÆREMIDLER_ENSLIG_FORSØRGER_BARNETILLEGG,
    LÆREMIDLER_AAP_BARNETILLEGG,
    LÆREMIDLER_ETTERLATTE_BARNETILLEGG,
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
