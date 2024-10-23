package utsjekk.iverksetting.v3

import utsjekk.ApiError.Companion.notFound
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

interface UtbetalingService {
    fun opprett(ny: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto
    fun korriger(ref: UUID, korrigert: Utbetaling): UtbetalingsoppdragDto
    fun opphør(ref: UUID, fom: LocalDate): UtbetalingsoppdragDto
}

object UtbetalingsoppdragService : UtbetalingService {
    /**
     * Opprett nytt utbetalingsoppdrag
     */
    override fun opprett(ny: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto {
        val forrigeUtbetaling = ny.ref?.let { ref -> DatabaseFake[ref] ?: notFound("utbetaling with ref $ref") }
        return UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = ny.ref == null,
            fagsystem = fagsystem,
            saksnummer = ny.sakId.id,
            aktør = ny.personident.ident,
            saksbehandlerId = ny.saksbehandlerId.ident,
            beslutterId = ny.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = ny.perioder.first().brukersNavKontor?.enhet,
            utbetalingsperiode = ny.perioder
                .sortedBy { it.fom } // TODO: vil ikke fungere ved ved forkorting/forlenging
                .fold(listOf()) { acc, periode ->
                    acc + if (acc.isEmpty()) {
                        UtbetalingsperiodeDto(
                            erEndringPåEksisterendePeriode = false,
                            opphør = null,
                            id = periode.id,
                            forrigeId = forrigeUtbetaling?.sistePeriode()?.id,
                            vedtaksdato = ny.vedtakstidspunkt.toLocalDate(),
                            klassekode = klassekode(periode.stønad),
                            fom = periode.fom,
                            tom = periode.tom,
                            sats = periode.beløp,
                            satstype = satstype(periode),
                            utbetalesTil = ny.personident.ident,
                            behandlingId = ny.behandlingId.id,
                        )
                    } else {
                        UtbetalingsperiodeDto(
                            erEndringPåEksisterendePeriode = false,
                            opphør = null,
                            id = periode.id,
                            forrigeId = acc.last().id,
                            vedtaksdato = ny.vedtakstidspunkt.toLocalDate(),
                            klassekode = klassekode(periode.stønad),
                            fom = periode.fom,
                            tom = periode.tom,
                            sats = periode.beløp,
                            satstype = satstype(periode),
                            utbetalesTil = ny.personident.ident,
                            behandlingId = ny.behandlingId.id,
                        )
                    }
                },
        )
    }

    override fun korriger(ref: UUID, korrigert: Utbetaling): UtbetalingsoppdragDto {
        TODO()
    }

    override fun opphør(ref: UUID, fom: LocalDate): UtbetalingsoppdragDto {
        TODO()
    }
}

private fun Utbetaling.sistePeriode() = perioder.maxBy { it.tom }

internal object DatabaseFake {
    private val utbetalinger = mutableMapOf<UtbetalingId, Utbetaling>()

    operator fun get(id: UtbetalingId): Utbetaling? {
        return utbetalinger[id]
    }

    operator fun set(id: UtbetalingId, utbetaling: Utbetaling) {
        utbetalinger[id] = utbetaling
    }

    fun truncate() = utbetalinger.clear()
}

private fun satstype(periode: Utbetalingsperiode): Satstype = when {
    periode.fom.dayOfMonth == 1 && periode.tom.plusDays(1) == periode.fom.plusMonths(1) -> Satstype.MÅNEDLIG
    periode.fom == periode.tom -> Satstype.DAGLIG
    else -> Satstype.ENGANGS
}

private fun klassekode(stønadstype: Stønadstype): String = when (stønadstype) {
    is Stønadstype.StønadTypeDagpenger -> klassekode(stønadstype)
    is Stønadstype.StønadTypeTilleggsstønader -> klassekode(stønadstype)
    is Stønadstype.StønadTypeTiltakspenger -> klassekode(stønadstype)
}

private fun klassekode(stønadstype: Stønadstype.StønadTypeTiltakspenger): String = when (stønadstype) {
    Stønadstype.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING -> TODO()
    Stønadstype.StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING -> TODO()
    Stønadstype.StønadTypeTiltakspenger.ARBEIDSTRENING -> TODO()
    Stønadstype.StønadTypeTiltakspenger.AVKLARING -> TODO()
    Stønadstype.StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB -> TODO()
    Stønadstype.StønadTypeTiltakspenger.ENKELTPLASS_AMO -> TODO()
    Stønadstype.StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG -> TODO()
    Stønadstype.StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET -> TODO()
    Stønadstype.StønadTypeTiltakspenger.GRUPPE_AMO -> TODO()
    Stønadstype.StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG -> TODO()
    Stønadstype.StønadTypeTiltakspenger.HØYERE_UTDANNING -> TODO()
    Stønadstype.StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE -> TODO()
    Stønadstype.StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG -> TODO()
    Stønadstype.StønadTypeTiltakspenger.JOBBKLUBB -> TODO()
    Stønadstype.StønadTypeTiltakspenger.OPPFØLGING -> TODO()
    Stønadstype.StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV -> TODO()
    Stønadstype.StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING -> TODO()
}

private fun klassekode(stønadstype: Stønadstype.StønadTypeTilleggsstønader): String = when (stønadstype) {
    Stønadstype.StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.TILSYN_BARN_AAP -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.LÆREMIDLER_AAP -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER_BARNETILLEGG -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.TILSYN_BARN_AAP_BARNETILLEGG -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE_BARNETILLEGG -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER_BARNETILLEGG -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.LÆREMIDLER_AAP_BARNETILLEGG -> TODO()
    Stønadstype.StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE_BARNETILLEGG -> TODO()
}

private fun klassekode(stønadstype: Stønadstype.StønadTypeDagpenger): String = when (stønadstype) {
    Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR -> "DPORAS"
    Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG -> "DPORASFE"
    Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD -> "DPORASFE-IOP"
    Stønadstype.StønadTypeDagpenger.PERMITTERING_ORDINÆR -> TODO()
    Stønadstype.StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG -> TODO()
    Stønadstype.StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG_AVDØD -> TODO()
    Stønadstype.StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI -> TODO()
    Stønadstype.StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI_FERIETILLEGG -> TODO()
    Stønadstype.StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI_FERIETILLEGG_AVDØD -> TODO()
    Stønadstype.StønadTypeDagpenger.EØS -> TODO()
    Stønadstype.StønadTypeDagpenger.EØS_FERIETILLEGG -> TODO()
    Stønadstype.StønadTypeDagpenger.EØS_FERIETILLEGG_AVDØD -> TODO()
}
