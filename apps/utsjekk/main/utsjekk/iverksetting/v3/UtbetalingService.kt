package utsjekk.iverksetting.v3

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

interface UtbetalingService {

    /**
     * Legg til nytt utbetalingsoppdrag.
     */
    fun create(utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto

    /**
     * Hent eksisterende utbetalingsoppdrag
     */
    fun read(id: UtbetalingId): UtbetalingsoppdragDto = TODO("not implemented")

    /**
     * Erstatt et utbetalingsoppdrag.
     *  - endre beløp på et oppdrag
     *  - endre periode på et oppdrag (f.eks. forkorte siste periode)
     *  - opphør fra og med en dato
     */
    fun update(id: UtbetalingId, utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto = TODO("not implemented")

    /**
     * Slett en utbetalingsperiode (opphør hele perioden).
     */
    fun delete(id: UtbetalingId): Unit = TODO("not implemented")
}

object UtbetalingsoppdragService : UtbetalingService {

    override fun create(utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto {
        return UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = true,
            fagsystem = fagsystem,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.periode.betalendeEnhet?.enhet,
            utbetalingsperiode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = false,
                opphør = null,
                id = UUID.randomUUID(), // trenger vi denne uten kjeding? utbetaling.periode.id,
                forrigeId = null,
                vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                klassekode = klassekode(utbetaling.stønad),
                fom = utbetaling.periode.fom,
                tom = utbetaling.periode.tom,
                sats = utbetaling.periode.beløp,
                satstype = utbetaling.periode.satstype,
                utbetalesTil = utbetaling.personident.ident,
                behandlingId = utbetaling.behandlingId.id,
            )
        )
    }

    // TODO: valider at utbetalingsperioder sine IDer er ivaretatt
    // TODO: kan opphør skje med en MÅNEDSSATS midt i perioden?
    // TODO: kan opphør skje med en ENGANGSSATS midt i perioden?
    // TODO: ha med opphørsdato gjør kanskje dette enklere
    // override fun update(id: UtbetalingId, utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto {
    //     val forrigeUtbetaling = DatabaseFake.findOrNull(id) ?: notFound("utbetaling with id $id")
    //
    //     fun Utbetaling.isMissing(periode: Utbetalingsperiode): Boolean {
    //         return this.perioder.map { it.fom to it.tom }.contains(periode.fom to periode.tom).not()
    //     }
    //
    //     fun Utbetaling.hasAll(perioder: List<Utbetalingsperiode>): Boolean {
    //         return this.perioder.map { it.fom to it.tom }.containsAll(perioder.map { it.fom to it.tom })
    //     }
    //
    //     fun opphørsdato(): LocalDate {
    //         return forrigeUtbetaling.perioder
    //             .first { fu -> fu.fom !in utbetaling.perioder.map { u -> u.fom } }
    //             .fom
    //     }
    //
    //     return if (utbetaling.isMissing(forrigeUtbetaling.førstePeriode())) {
    //         opphør(
    //             opphør = opphørsdato(),
    //             utbetaling = utbetaling,
    //             forrigeUtbetaling = forrigeUtbetaling,
    //             fagsystem = fagsystem
    //         )
    //     } else if (utbetaling.hasAll(forrigeUtbetaling.perioder)) {
    //         korriger(utbetaling, forrigeUtbetaling.førstePeriode(), fagsystem)
    //     } else {
    //         endring(utbetaling, forrigeUtbetaling.sistePeriode(), fagsystem)
    //     }
    // }

    // private fun opphør(
    //     opphør: LocalDate,
    //     utbetaling: Utbetaling,
    //     forrigeUtbetaling: Utbetaling,
    //     fagsystem: FagsystemDto,
    // ): UtbetalingsoppdragDto {
    //     val forrigePeriode = forrigeUtbetaling.sistePeriode()
    //     val opphørsmelding = UtbetalingsperiodeDto(
    //         erEndringPåEksisterendePeriode = false, // TODO
    //         opphør = Opphør(opphør),
    //         id = UUID.randomUUID(),
    //         forrigeId = forrigePeriode.id,
    //         vedtaksdato = forrigeUtbetaling.vedtakstidspunkt.toLocalDate(),
    //         klassekode = klassekode(forrigePeriode.stønad),
    //         fom = forrigePeriode.fom,
    //         tom = forrigePeriode.tom,
    //         sats = forrigePeriode.beløp,
    //         satstype = satstype(forrigePeriode),
    //         utbetalesTil = forrigeUtbetaling.personident.ident,
    //         behandlingId = forrigeUtbetaling.behandlingId.id,
    //     )
    //     return UtbetalingsoppdragDto(
    //         erFørsteUtbetalingPåSak = utbetaling.ref == null,
    //         fagsystem = fagsystem,
    //         saksnummer = utbetaling.sakId.id,
    //         aktør = utbetaling.personident.ident,
    //         saksbehandlerId = utbetaling.saksbehandlerId.ident,
    //         beslutterId = utbetaling.beslutterId.ident,
    //         avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    //         brukersNavKontor = utbetaling.perioder.last().brukersNavKontor?.enhet,
    //         utbetalingsperiode = utbetaling.perioder
    //             .sortedBy { it.fom }
    //             .fold(listOf(opphørsmelding)) { acc, periode ->
    //                 acc + UtbetalingsperiodeDto(
    //                     erEndringPåEksisterendePeriode = false, // TODO
    //                     opphør = null,
    //                     id = UUID.randomUUID(),
    //                     forrigeId = acc.lastOrNull()?.id,
    //                     vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
    //                     klassekode = klassekode(periode.stønad),
    //                     fom = periode.fom,
    //                     tom = periode.tom,
    //                     sats = periode.beløp,
    //                     satstype = satstype(periode),
    //                     utbetalesTil = utbetaling.personident.ident,
    //                     behandlingId = utbetaling.behandlingId.id,
    //                 )
    //             },
    //     )
    // }

    // private fun korriger(
    //     utbetaling: Utbetaling,
    //     refPeriode: Utbetalingsperiode,
    //     fagsystem: FagsystemDto
    // ): UtbetalingsoppdragDto {
    //     return UtbetalingsoppdragDto(
    //         erFørsteUtbetalingPåSak = utbetaling.ref == null,
    //         fagsystem = fagsystem,
    //         saksnummer = utbetaling.sakId.id,
    //         aktør = utbetaling.personident.ident,
    //         saksbehandlerId = utbetaling.saksbehandlerId.ident,
    //         beslutterId = utbetaling.beslutterId.ident,
    //         avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    //         brukersNavKontor = utbetaling.perioder.last().brukersNavKontor?.enhet,
    //         utbetalingsperiode = utbetaling.perioder
    //             .sortedBy { it.fom }
    //             .fold(listOf()) { acc, periode ->
    //                 acc + UtbetalingsperiodeDto(
    //                     erEndringPåEksisterendePeriode = false, // TODO
    //                     opphør = null,
    //                     id = UUID.randomUUID(),
    //                     forrigeId = acc.lastOrNull()?.id ?: refPeriode.id,
    //                     vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
    //                     klassekode = klassekode(periode.stønad),
    //                     fom = periode.fom,
    //                     tom = periode.tom,
    //                     sats = periode.beløp,
    //                     satstype = satstype(periode),
    //                     utbetalesTil = utbetaling.personident.ident,
    //                     behandlingId = utbetaling.behandlingId.id,
    //                 )
    //             },
    //     )
    // }

    // TODO: Endring hvis man f.eks gjenbruker både sak og behandlingId.
    //  Kan brukes til å lege til fler stønader i samme behandling
    private fun endring(
        utbetaling: Utbetaling,
        refPeriode: Utbetalingsperiode,
        fagsystem: FagsystemDto
    ): UtbetalingsoppdragDto {
        TODO()
    }
}

// internal fun Utbetaling.sistePeriode() = perioder.maxBy { it.tom }
// internal fun Utbetaling.førstePeriode() = perioder.minBy { it.fom }

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
    periode.fom.dayOfMonth == 1 && periode.tom.plusDays(1) == periode.fom.plusMonths(1) -> Satstype.MND
    periode.fom == periode.tom -> Satstype.DAG
    else -> Satstype.ENGANGS
}

private fun klassekode(stønadstype: Stønadstype): String = when (stønadstype) {
    is StønadTypeDagpenger -> klassekode(stønadstype)
    is StønadTypeTilleggsstønader -> klassekode(stønadstype)
    is StønadTypeTiltakspenger -> klassekode(stønadstype)
}

private fun klassekode(stønadstype: StønadTypeTiltakspenger): String = when (stønadstype) {
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
