package utsjekk.iverksetting.v3

import utsjekk.ApiError.Companion.notFound
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

interface UtbetalingService {
    /**
     * Legg til nytt utbetalingsoppdrag.
     *  - nytt oppdrag
     *  - kjede med et tidligere oppdrag (f.eks. forlenge)
     */
    fun create(ny: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto

    /**
     * Erstatt et utbetalingsoppdrag.
     *  - endre beløp på et oppdrag
     *  - endre periode på et oppdrag (f.eks. forkorte)
     */
    fun replace(id: UtbetalingId, korrigert: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto

    /**
     * Opphør et utbetalingsoppdrag.
     *  - opphør mellom to datoer
     *  - opphør fra og med en dato
     */
    fun stop(id: UtbetalingId, fom: LocalDate, tom: LocalDate?, fagsystem: FagsystemDto): UtbetalingsoppdragDto
}

object UtbetalingsoppdragService : UtbetalingService {
    override fun create(ny: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto {
        val forrigeUtbetaling = ny.ref?.let { ref ->
            DatabaseFake.findOrNull(ref) ?: notFound("utbetaling with ref $ref")
        }

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
                .sortedBy { it.fom }
                .fold(listOf()) { acc, periode ->
                    acc + UtbetalingsperiodeDto(
                        erEndringPåEksisterendePeriode = false,
                        opphør = null,
                        id = periode.id,
                        forrigeId = acc.lastOrNull()?.id ?: forrigeUtbetaling?.sistePeriode()?.id,
                        vedtaksdato = ny.vedtakstidspunkt.toLocalDate(),
                        klassekode = klassekode(periode.stønad),
                        fom = periode.fom,
                        tom = periode.tom,
                        sats = periode.beløp,
                        satstype = satstype(periode),
                        utbetalesTil = ny.personident.ident,
                        behandlingId = ny.behandlingId.id,
                    )
                },
        )
    }

    // TODO: valider at korrigerte perioder har fått nye IDer
    override fun replace(id: UtbetalingId, korrigert: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto {
        val forrigeUtbetaling = DatabaseFake.findOrNull(id) ?: notFound("utbetaling with id $id")
        return UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = korrigert.ref == null,
            fagsystem = fagsystem,
            saksnummer = korrigert.sakId.id,
            aktør = korrigert.personident.ident,
            saksbehandlerId = korrigert.saksbehandlerId.ident,
            beslutterId = korrigert.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = korrigert.perioder.first().brukersNavKontor?.enhet,
            utbetalingsperiode = korrigert.perioder
                .sortedBy { it.fom }
                .fold(listOf()) { acc, periode ->
                    acc + UtbetalingsperiodeDto(
                        erEndringPåEksisterendePeriode = false,
                        opphør = null,
                        id = periode.id,
                        forrigeId = acc.lastOrNull()?.id ?: forrigeUtbetaling.førstePeriode().id,
                        vedtaksdato = korrigert.vedtakstidspunkt.toLocalDate(),
                        klassekode = klassekode(periode.stønad),
                        fom = periode.fom,
                        tom = periode.tom,
                        sats = periode.beløp,
                        satstype = satstype(periode),
                        utbetalesTil = korrigert.personident.ident,
                        behandlingId = korrigert.behandlingId.id,
                    )
                },
        )
    }

    override fun stop(
        id: UtbetalingId,
        fom: LocalDate,
        tom: LocalDate?,
        fagsystem: FagsystemDto
    ): UtbetalingsoppdragDto {
        TODO()
    }
}

internal fun Utbetaling.sistePeriode() = perioder.maxBy { it.tom }
internal fun Utbetaling.førstePeriode() = perioder.minBy { it.fom }

internal object DatabaseFake {
    private val utbetalinger = mutableMapOf<UtbetalingId, Utbetaling>()

    fun findOrNull(id: UtbetalingId): Utbetaling? {
        return utbetalinger[id]
    }

    fun save(utbetaling: Utbetaling): UtbetalingId {
        val id = UtbetalingId(UUID.randomUUID())
        utbetalinger[id] = utbetaling
        return id
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
