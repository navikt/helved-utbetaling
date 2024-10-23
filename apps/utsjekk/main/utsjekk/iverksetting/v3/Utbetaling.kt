package utsjekk.iverksetting.v3

import java.time.*
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
value class Navident(val ident: String)

@JvmInline
value class UtbetalingId(val id: UUID) {
    companion object
}

data class Utbetaling(
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val saksbehandlerId: Navident,
    val beslutterId: Navident,
    val perioder: List<Utbetalingsperiode>,
    val ref: UtbetalingId? = null
)

@JvmInline
value class NavEnhet(val enhet: String)

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val stønad: Stønadstype,
    val id: UUID = UUID.randomUUID(),

    /**
     * Dette feltet brukes hvis budsjettet til et lokalkontor skal brukes i beregningene.
     */
    val brukersNavKontor: NavEnhet? = null,

    /**
     * Dagpenger har særegen skatteberegning og må fylle inn dette feltet.
     * Navnet på feltet er ikke bestemt enda.
     */
    val fastsattDagsats: UInt? = null,
)

sealed interface Stønadstype {
    companion object {
        fun valueOf(str: String): Stønadstype =
            runCatching { StønadTypeDagpenger.valueOf(str) }
                .recoverCatching { StønadTypeTilleggsstønader.valueOf(str) }
                .recoverCatching { StønadTypeTiltakspenger.valueOf(str) }
                .getOrThrow()
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
        EØS_FERIETILLEGG_AVDØD,
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
}
