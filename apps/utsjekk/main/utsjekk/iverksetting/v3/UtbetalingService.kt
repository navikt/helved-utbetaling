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
    fun create(utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto

    /**
     * Erstatt et utbetalingsoppdrag.
     *  - endre beløp på et oppdrag
     *  - endre periode på et oppdrag (f.eks. forkorte siste periode)
     *  - opphør fra og med en dato
     */
    fun update(id: UtbetalingId, utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto

    fun read(id: UtbetalingId): UtbetalingsoppdragDto = TODO("not implemented")

    /**
     * Slett en utbetalingsperiode.
     *  - opphør hele perioden
     */
    fun delete(id: UtbetalingId): Unit = TODO("not implemented")
}

object UtbetalingsoppdragService : UtbetalingService {
    override fun create(utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto {
        val forrigeUtbetaling = utbetaling.ref?.let { ref ->
            DatabaseFake.findOrNull(ref) ?: notFound("utbetaling with ref $ref")
        }

        return UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = utbetaling.ref == null,
            fagsystem = fagsystem,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.perioder.last().brukersNavKontor?.enhet,
            utbetalingsperiode = utbetaling.perioder
                .sortedBy { it.fom }
                .fold(listOf()) { acc, periode ->
                    acc + UtbetalingsperiodeDto(
                        erEndringPåEksisterendePeriode = false,
                        opphør = null,
                        id = periode.id,
                        forrigeId = acc.lastOrNull()?.id ?: forrigeUtbetaling?.sistePeriode()?.id,
                        vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                        klassekode = klassekode(periode.stønad),
                        fom = periode.fom,
                        tom = periode.tom,
                        sats = periode.beløp,
                        satstype = satstype(periode),
                        utbetalesTil = utbetaling.personident.ident,
                        behandlingId = utbetaling.behandlingId.id,
                    )
                },
        )
    }

    // TODO: valider at utbetalingsperioder sine IDer er ivaretatt
    // TODO: kan opphør skje med en MÅNEDSSATS midt i perioden?
    // TODO: kan opphør skje med en ENGANGSSATS midt i perioden?
    // TODO: ha med opphørsdato gjør kanskje dette enklere
    override fun update(id: UtbetalingId, utbetaling: Utbetaling, fagsystem: FagsystemDto): UtbetalingsoppdragDto {
        val forrigeUtbetaling = DatabaseFake.findOrNull(id) ?: notFound("utbetaling with id $id")

        fun Utbetaling.isMissing(periode: Utbetalingsperiode): Boolean {
            return this.perioder.map { it.fom to it.tom }.contains(periode.fom to periode.tom).not()
        }

        fun Utbetaling.hasAll(perioder: List<Utbetalingsperiode>): Boolean {
            return this.perioder.map { it.fom to it.tom }.containsAll(perioder.map { it.fom to it.tom })
        }

        fun opphørsdato(): LocalDate {
            return forrigeUtbetaling.perioder
                .first { fu -> fu.fom !in utbetaling.perioder.map { u -> u.fom } }
                .fom
        }

        return if (utbetaling.isMissing(forrigeUtbetaling.førstePeriode())) {
            opphør(
                opphør = opphørsdato(),
                utbetaling = utbetaling,
                forrigeUtbetaling = forrigeUtbetaling,
                fagsystem = fagsystem
            )
        } else if (utbetaling.hasAll(forrigeUtbetaling.perioder)) {
            korriger(utbetaling, forrigeUtbetaling.førstePeriode(), fagsystem)
        } else {
            endring(utbetaling, forrigeUtbetaling.sistePeriode(), fagsystem)
        }
    }

    private fun opphør(
        opphør: LocalDate,
        utbetaling: Utbetaling,
        forrigeUtbetaling: Utbetaling,
        fagsystem: FagsystemDto,
    ): UtbetalingsoppdragDto {
        val forrigePeriode = forrigeUtbetaling.sistePeriode()
        val opphørsmelding = UtbetalingsperiodeDto(
            erEndringPåEksisterendePeriode = false, // TODO
            opphør = Opphør(opphør),
            id = UUID.randomUUID(),
            forrigeId = forrigePeriode.id,
            vedtaksdato = forrigeUtbetaling.vedtakstidspunkt.toLocalDate(),
            klassekode = klassekode(forrigePeriode.stønad),
            fom = forrigePeriode.fom,
            tom = forrigePeriode.tom,
            sats = forrigePeriode.beløp,
            satstype = satstype(forrigePeriode),
            utbetalesTil = forrigeUtbetaling.personident.ident,
            behandlingId = forrigeUtbetaling.behandlingId.id,
        )
        return UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = utbetaling.ref == null,
            fagsystem = fagsystem,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.perioder.last().brukersNavKontor?.enhet,
            utbetalingsperiode = utbetaling.perioder
                .sortedBy { it.fom }
                .fold(listOf(opphørsmelding)) { acc, periode ->
                    acc + UtbetalingsperiodeDto(
                        erEndringPåEksisterendePeriode = false, // TODO
                        opphør = null,
                        id = UUID.randomUUID(),
                        forrigeId = acc.lastOrNull()?.id,
                        vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                        klassekode = klassekode(periode.stønad),
                        fom = periode.fom,
                        tom = periode.tom,
                        sats = periode.beløp,
                        satstype = satstype(periode),
                        utbetalesTil = utbetaling.personident.ident,
                        behandlingId = utbetaling.behandlingId.id,
                    )
                },
        )
    }

    private fun korriger(
        utbetaling: Utbetaling,
        refPeriode: Utbetalingsperiode,
        fagsystem: FagsystemDto
    ): UtbetalingsoppdragDto {
        return UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = utbetaling.ref == null,
            fagsystem = fagsystem,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.perioder.last().brukersNavKontor?.enhet,
            utbetalingsperiode = utbetaling.perioder
                .sortedBy { it.fom }
                .fold(listOf()) { acc, periode ->
                    acc + UtbetalingsperiodeDto(
                        erEndringPåEksisterendePeriode = false, // TODO
                        opphør = null,
                        id = UUID.randomUUID(),
                        forrigeId = acc.lastOrNull()?.id ?: refPeriode.id,
                        vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                        klassekode = klassekode(periode.stønad),
                        fom = periode.fom,
                        tom = periode.tom,
                        sats = periode.beløp,
                        satstype = satstype(periode),
                        utbetalesTil = utbetaling.personident.ident,
                        behandlingId = utbetaling.behandlingId.id,
                    )
                },
        )
    }

    private fun endring(
        utbetaling: Utbetaling,
        refPeriode: Utbetalingsperiode,
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
