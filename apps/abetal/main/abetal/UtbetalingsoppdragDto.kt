package abetal

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.math.BigDecimal

enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"), // TDOO: trenger ikke koden i denne appen
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST");

    companion object {
        fun from(stønad: Stønadstype): FagsystemDto {
            return FagsystemDto.entries
                .find { it.name == stønad.asFagsystemStr() }
                ?: badRequest("$stønad er ukjent fagsystem")
        }
    }
}

data class UtbetalingsoppdragDto(
    val uid: UtbetalingId,
    val erFørsteUtbetalingPåSak: Boolean,
    val fagsystem: FagsystemDto,
    val saksnummer: String,
    val aktør: String,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalingsperiode: UtbetalingsperiodeDto,
    val avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    val brukersNavKontor: String? = null,
) {
    companion object;

    fun into() = no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag(
        erFørsteUtbetalingPåSak= erFørsteUtbetalingPåSak,
        fagsystem= when (fagsystem) {
            FagsystemDto.DAGPENGER -> no.nav.utsjekk.kontrakter.felles.Fagsystem.DAGPENGER
            FagsystemDto.TILTAKSPENGER -> no.nav.utsjekk.kontrakter.felles.Fagsystem.TILTAKSPENGER
            FagsystemDto.TILLEGGSSTØNADER -> no.nav.utsjekk.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER
        },
        saksnummer= saksnummer,
        iverksettingId= null,
        aktør= aktør,
        saksbehandlerId= saksbehandlerId,
        beslutterId= beslutterId,
        avstemmingstidspunkt= avstemmingstidspunkt,
        utbetalingsperiode= listOf(utbetalingsperiode.into()),
        brukersNavKontor= brukersNavKontor,
    )
}

data class UtbetalingsperiodeDto(
    val erEndringPåEksisterendePeriode: Boolean,
    val id: UInt,
    val vedtaksdato: LocalDate,
    val klassekode: String, // TODO: trenger ikke klassekode i denne appen
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: UInt,
    val satstype: Satstype,
    val utbetalesTil: String,
    val behandlingId: String,
    val opphør: Opphør? = null,
) {
    companion object;

    fun into() = no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode(
        erEndringPåEksisterendePeriode= erEndringPåEksisterendePeriode,
        opphør= opphør?.let { no.nav.utsjekk.kontrakter.oppdrag.Opphør(it.fom) },
        periodeId= id.hashCode().toLong(), // TODO: denne skal byttes ut med en long i databasen også
        forrigePeriodeId= null,
        vedtaksdato= vedtaksdato,
        klassifisering= klassekode,
        fom= fom,
        tom= tom,
        sats= BigDecimal(sats.toDouble()),
        satstype= when (satstype) {
            Satstype.DAG ->no.nav.utsjekk.kontrakter.felles.Satstype.DAGLIG_INKL_HELG
            Satstype.VIRKEDAG ->no.nav.utsjekk.kontrakter.felles.Satstype.DAGLIG
            Satstype.MND ->no.nav.utsjekk.kontrakter.felles.Satstype.MÅNEDLIG
            Satstype.ENGANGS ->no.nav.utsjekk.kontrakter.felles.Satstype.ENGANGS
        },
        utbetalesTil= utbetalesTil,
        behandlingId= behandlingId,
        utbetalingsgrad= null,
    )
}

data class Opphør(val fom: LocalDate)

fun satstype(periode: Utbetalingsperiode): Satstype = when {
    periode.fom.dayOfMonth == 1 && periode.tom.plusDays(1) == periode.fom.plusMonths(1) -> Satstype.MND
    periode.fom == periode.tom -> Satstype.DAG
    else -> Satstype.ENGANGS
}

fun klassekode(stønadstype: Stønadstype): String = when (stønadstype) {
    is StønadTypeDagpenger -> klassekode(stønadstype)
    is StønadTypeTilleggsstønader -> klassekode(stønadstype)
    is StønadTypeTiltakspenger -> klassekode(stønadstype)
}

fun klassekode(stønadstype: StønadTypeTiltakspenger): String = when (stønadstype) {
    StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING -> TODO()
    StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING -> TODO()
    StønadTypeTiltakspenger.ARBEIDSTRENING -> TODO()
    StønadTypeTiltakspenger.AVKLARING -> TODO()
    StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB -> TODO()
    StønadTypeTiltakspenger.ENKELTPLASS_AMO -> TODO()
    StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG -> TODO()
    StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET -> TODO()
    StønadTypeTiltakspenger.GRUPPE_AMO -> TODO()
    StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG -> TODO()
    StønadTypeTiltakspenger.HØYERE_UTDANNING -> TODO()
    StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE -> TODO()
    StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG -> TODO()
    StønadTypeTiltakspenger.JOBBKLUBB -> TODO()
    StønadTypeTiltakspenger.OPPFØLGING -> TODO()
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV -> TODO()
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING -> TODO()
}

private fun klassekode(stønadstype: StønadTypeTilleggsstønader): String = when (stønadstype) {
    StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER -> TODO()
    StønadTypeTilleggsstønader.TILSYN_BARN_AAP -> TODO()
    StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE -> TODO()
    StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER -> TODO()
    StønadTypeTilleggsstønader.LÆREMIDLER_AAP -> TODO()
    StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE -> TODO()
    StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER_BARNETILLEGG -> TODO()
    StønadTypeTilleggsstønader.TILSYN_BARN_AAP_BARNETILLEGG -> TODO()
    StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE_BARNETILLEGG -> TODO()
    StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER_BARNETILLEGG -> TODO()
    StønadTypeTilleggsstønader.LÆREMIDLER_AAP_BARNETILLEGG -> TODO()
    StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE_BARNETILLEGG -> TODO()
}

private fun klassekode(stønadstype: StønadTypeDagpenger): String = when (stønadstype) {
    StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR -> "DPORAS"
    StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG -> "DPORASFE"
    StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD -> "DPORASFE-IOP"
    StønadTypeDagpenger.PERMITTERING_ORDINÆR -> TODO()
    StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG -> TODO()
    StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG_AVDØD -> TODO()
    StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI -> TODO()
    StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI_FERIETILLEGG -> TODO()
    StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI_FERIETILLEGG_AVDØD -> TODO()
    StønadTypeDagpenger.EØS -> TODO()
    StønadTypeDagpenger.EØS_FERIETILLEGG -> TODO()
    StønadTypeDagpenger.EØS_FERIETILLEGG_AVDØD -> TODO()
}
