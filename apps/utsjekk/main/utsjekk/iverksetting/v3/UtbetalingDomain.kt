package utsjekk.iverksetting.v3

import com.fasterxml.jackson.annotation.JsonCreator
import utsjekk.avstemming.erVirkedag
import utsjekk.badRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@JvmInline
value class SakId(val id: String)

@JvmInline
value class BehandlingId(val id: String)

@JvmInline
value class Personident(val ident: String) {
    companion object
}

@JvmInline
value class Navident(val ident: String) {
    companion object
}

@JvmInline
value class NavEnhet(val enhet: String)


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
        fun from(dto: UtbetalingApi): Utbetaling =
            Utbetaling(
                sakId = dto.sakId,
                behandlingId = dto.behandlingId,
                personident = dto.personident,
                vedtakstidspunkt = dto.vedtakstidspunkt,
                stønad = dto.stønad,
                beslutterId = dto.beslutterId,
                saksbehandlerId = dto.saksbehandlerId,
                periode = Utbetalingsperiode.from(dto.perioder.sortedBy { it.fom }),
            )
    }
}

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val satstype: Satstype,
    val id: UUID = UUID.randomUUID(),
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
                id = UUID.randomUUID(),
                betalendeEnhet = perioder.last().betalendeEnhet, // baserer oss på lastest news
                fastsattDagpengesats = perioder.last().fastsattDagpengesats, // baserer oss på lastest news
                satstype = satstype,
            )
        }
    }
}

enum class Satstype { DAG, VIRKEDAG, MND, ENGANGS }

sealed interface Stønadstype {

    companion object {
        @JsonCreator
        @JvmStatic
        fun valueOf(str: String): Stønadstype =
            runCatching { StønadTypeDagpenger.valueOf(str) }
                .recoverCatching { StønadTypeTilleggsstønader.valueOf(str) }
                .recoverCatching { StønadTypeTiltakspenger.valueOf(str) }
                .getOrThrow()

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
    EØS_FERIETILLEGG_AVDØD;
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

private fun satstype(fom: LocalDate, tom: LocalDate): Satstype = when {
    fom.dayOfMonth == 1 && tom.plusDays(1) == fom.plusMonths(1) -> Satstype.MND
    fom == tom -> if (fom.erVirkedag()) Satstype.VIRKEDAG else Satstype.DAG
    else -> Satstype.ENGANGS
}

private fun satstype(satstyper: List<Satstype>): Satstype {
    if (satstyper.size == 1 && satstyper.any { it in listOf(Satstype.ENGANGS, Satstype.DAG, Satstype.VIRKEDAG) }) {
        return Satstype.ENGANGS
    }
    if (satstyper.all { it == Satstype.VIRKEDAG }) {
        return Satstype.VIRKEDAG
    }
    if (satstyper.all { it in listOf(Satstype.DAG, Satstype.VIRKEDAG) }) {
        return Satstype.DAG
    }
    if (satstyper.size == 1 && satstyper.all { it == Satstype.MND }) {
        return Satstype.MND
    }

    badRequest(
        msg = "inkonsistens blant datoene i periodene.",
        doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
    )
}

private fun beløp(perioder: List<UtbetalingsperiodeApi>, satstype: Satstype): UInt =
    when (satstype) {
        Satstype.DAG, Satstype.VIRKEDAG -> perioder.map { it.beløp }.toSet().singleOrNull() 
            ?: badRequest(
                msg = "fant fler ulike beløp blant dagene",
                field = "beløp",
                doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
            )

        else -> perioder.singleOrNull()?.beløp 
            ?: badRequest(
                msg = "forventet kun en periode, da sammenslåing av beløp ikke er støttet",
                field = "beløp",
                doc = "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder"
            )
    }

