package utsjekk.utbetaling

import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.task.Tasks
import libs.utils.Result
import no.nav.utsjekk.kontrakter.felles.objectMapper
import utsjekk.notFound
import utsjekk.locked
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object UtbetalingService {

    /**
     * Legg til nytt utbetalingsoppdrag.
     */
    suspend fun create(uid: UtbetalingId, utbetaling: Utbetaling): Result<Unit, DatabaseError> {
        // TODO: finnes det noe fra før dersom det er sendt inn 1 periode som senere har blitt slettet/annulert/opphørt?
        val erFørsteUtbetalingPåSak = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.find(utbetaling.sakId, history = true)
                    .map { it.stønad.asFagsystemStr() }
                    .none { it == utbetaling.stønad.asFagsystemStr() }
            }
        }

        var forrigeId: PeriodeId? = null
        val oppdrag = UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = erFørsteUtbetalingPåSak,
            fagsystem = FagsystemDto.from(utbetaling.stønad),
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.perioder.betalendeEnhet()?.enhet,
            avvent = utbetaling.avvent?.let { avvent ->
                AvventDto(
                    fom = avvent.fom,
                    tom = avvent.tom,
                    overføres = avvent.overføres,
                    årsak = avvent.årsak,
                    feilregistrering = avvent.feilregistrering
                )
            },
            utbetalingsperioder = utbetaling.perioder.mapIndexed { i, periode ->
                val id = if(i == utbetaling.perioder.size - 1) utbetaling.lastPeriodeId else PeriodeId()

                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = id.toString(),
                    forrigePeriodeId = forrigeId?.toString().also { forrigeId = id},
                    vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                    klassekode = klassekode(utbetaling.stønad),
                    fom = periode.fom,
                    tom = periode.tom,
                    sats = periode.beløp,
                    satstype = utbetaling.satstype,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                    fastsattDagsats = periode.fastsattDagsats
                )
            }
        )

        return withContext(Jdbc.context) {
            transaction {
                Tasks.create(libs.task.Kind.Utbetaling, oppdrag) {
                    objectMapper.writeValueAsString(it)
                }
                UtbetalingDao(utbetaling, Status.IKKE_PÅBEGYNT).insert(uid)
            }
        }
    }

    /**
     * Hent eksisterende utbetalingsoppdrag
     */
    suspend fun read(uid: UtbetalingId): Utbetaling? {
        return withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid)?.data
            }
        }
    }

    /**
     * Hent eksisterende utbetalingsoppdrag
     */
    suspend fun status(uid: UtbetalingId): Status {
        return withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid)?.status ?: notFound("status for utbetaling", "uid")
            }
        }
    }

    /**
     * Erstatt et utbetalingsoppdrag.
     *  - endre beløp på et oppdrag
     *  - endre periode på et oppdrag (f.eks. forkorte siste periode)
     *  - opphør fra og med en dato
     */
    suspend fun update(uid: UtbetalingId, utbetaling: Utbetaling): Result<Unit, DatabaseError> {
        val dao = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid) ?: notFound(msg = "existing utbetaling", field = "uid")
            }
        }

        if(dao.status != Status.OK) {
            locked("utbetalingen har et pågående oppdrag, vent til dette er ferdig") 
        }

        val existing = dao.data

        existing.validateLockedFields(utbetaling)
        existing.validateMinimumChanges(utbetaling)

        val oppdrag = UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.from(utbetaling.stønad),
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.perioder.betalendeEnhet()?.enhet,
            avvent = utbetaling.avvent?.let { avvent ->
                AvventDto(
                    fom = avvent.fom,
                    tom = avvent.tom,
                    overføres = avvent.overføres,
                    årsak = avvent.årsak,
                    feilregistrering = avvent.feilregistrering
                )
            },
            utbetalingsperioder = Utbetalingsperioder.utled(existing, utbetaling)
        )
        return withContext(Jdbc.context) {
            transaction {
                Tasks.create(libs.task.Kind.Utbetaling, oppdrag) {
                    objectMapper.writeValueAsString(it)
                }

                val newLastPeriodeId = PeriodeId.decode(oppdrag.utbetalingsperioder.last().id)
                UtbetalingDao(data = utbetaling.copy(lastPeriodeId = newLastPeriodeId)).insert(uid)
            }
        }
    }

    /**
     * Slett en utbetalingsperiode (opphør hele perioden).
     */
    suspend fun delete(uid: UtbetalingId, utbetaling: Utbetaling): Result<Unit, DatabaseError> {
        val dao = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid) ?: notFound(msg = "existing utbetaling", field = "uid")
            }
        }

        if(dao.status != Status.OK) {
            locked("utbetalingen har et pågående oppdrag, vent til dette er ferdig") 
        }

        val existing = dao.data

        existing.validateLockedFields(utbetaling)

        val oppdrag = UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.from(utbetaling.stønad),
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.perioder.betalendeEnhet()?.enhet,
            avvent = utbetaling.avvent?.let { avvent ->
                AvventDto(
                    fom = avvent.fom,
                    tom = avvent.tom,
                    overføres = avvent.overføres,
                    årsak = avvent.årsak,
                    feilregistrering = avvent.feilregistrering
                )
            },
            utbetalingsperioder = listOf(utbetaling.perioder.maxBy { it.fom }.let { sistePeriode ->
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = true, // opphør er alltid en ENDR
                    opphør = Opphør(utbetaling.perioder.minBy { it.fom }.fom),
                    id = existing.lastPeriodeId.toString(), // endrer på eksisterende delytelseId
                    vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                    klassekode = klassekode(utbetaling.stønad),
                    fom = sistePeriode.fom,
                    tom = sistePeriode.tom,
                    sats = sistePeriode.beløp,
                    satstype = utbetaling.satstype,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                    fastsattDagsats = sistePeriode.fastsattDagsats,
                )
            })
        )
        return withContext(Jdbc.context) {
            transaction {
                Tasks.create(libs.task.Kind.Utbetaling, oppdrag) {
                    objectMapper.writeValueAsString(it)
                }
                UtbetalingDao.delete(uid)
            }
        }
    }

    suspend fun lastOrNull(uid: UtbetalingId): Utbetaling? {
        return withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid, history = true)?.data
            }
        }
    }
}

internal fun klassekode(stønadstype: Stønadstype): String = when (stønadstype) {
    is StønadTypeDagpenger -> klassekode(stønadstype)
    is StønadTypeTilleggsstønader -> klassekode(stønadstype)
    is StønadTypeTiltakspenger -> klassekode(stønadstype)
    is StønadTypeAAP -> klassekode(stønadstype)
}

private fun klassekode(stønadstype: StønadTypeTiltakspenger): String = when (stønadstype) {
    StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING_BARN -> "TPTPAFT"
    StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING_BARN -> "TPTPARREHABAGDAG"
    StønadTypeTiltakspenger.ARBEIDSTRENING_BARN -> "TPTPATT"
    StønadTypeTiltakspenger.AVKLARING_BARN -> "TPTPAAG"
    StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB_BARN -> "TPTPDJB"
    StønadTypeTiltakspenger.ENKELTPLASS_AMO_BARN -> "TPTPEPAMO"
    StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN -> "TPTPEPVGSHOU"
    StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN -> "TPTPFLV"
    StønadTypeTiltakspenger.GRUPPE_AMO_BARN -> "TPTPGRAMO"
    StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN -> "TPTPGRVGSHOY"
    StønadTypeTiltakspenger.HØYERE_UTDANNING_BARN -> "TPTPHOYUTD"
    StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE_BARN -> "TPTPIPS"
    StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG_BARN -> "TPTPIPSUNG"
    StønadTypeTiltakspenger.JOBBKLUBB_BARN -> "TPTPJK2009"
    StønadTypeTiltakspenger.OPPFØLGING_BARN -> "TPTPOPPFAG"
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV_BARN -> "TPTPUAOPPF"
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN -> "TPTPUOPPFOPPL"
    StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING -> "TPTPAFT"
    StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING -> "TPTPARREHABAGDAG"
    StønadTypeTiltakspenger.ARBEIDSTRENING -> "TPTPATT"
    StønadTypeTiltakspenger.AVKLARING -> "TPTPAAG"
    StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB -> "TPTPDJB"
    StønadTypeTiltakspenger.ENKELTPLASS_AMO -> "TPTPEPAMO"
    StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG -> "TPTPEPVGSHOU"
    StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET -> "TPTPFLV"
    StønadTypeTiltakspenger.GRUPPE_AMO -> "TPTPGRAMO"
    StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG -> "TPTPGRVGSHOY"
    StønadTypeTiltakspenger.HØYERE_UTDANNING -> "TPTPHOYUTD"
    StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE -> "TPTPIPS"
    StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG -> "TPTPIPSUNG"
    StønadTypeTiltakspenger.JOBBKLUBB -> "TPTPJK2009"
    StønadTypeTiltakspenger.OPPFØLGING -> "TPTPOPPFAG"
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV -> "TPTPUAOPPF"
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING -> "TPTPUOPPFOPPL"
}

private fun klassekode(stønadstype: StønadTypeTilleggsstønader): String = when (stønadstype) {

    StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER -> "TSTBASISP2-OP"
    StønadTypeTilleggsstønader.TILSYN_BARN_AAP -> "TSTBASISP4-OP"
    StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE -> "TSTBASISP5-OP"
    StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER -> "TSLMASISP2-OP"
    StønadTypeTilleggsstønader.LÆREMIDLER_AAP -> "TSLMASISP3-OP"
    StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE -> "TSLMASISP4-OP"
}

private fun klassekode(stønadstype: StønadTypeDagpenger): String = when (stønadstype) {
    StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR -> "DPORAS"
    StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG -> "DPORASFE"
    StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD -> "DPORASFE-IOP"
    StønadTypeDagpenger.PERMITTERING_ORDINÆR -> "DPPEASFE1"
    StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG -> "DPPEAS"
    StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG_AVDØD -> "DPPEASFE1-IOP"
    StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI -> "DPPEFIFE1"
    StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI_FERIETILLEGG -> "DPPEFI"
    StønadTypeDagpenger.PERMITTERING_FISKEINDUSTRI_FERIETILLEGG_AVDØD -> "DPPEFIFE1-IOP"
    StønadTypeDagpenger.EØS -> "DPFEASISP"
    StønadTypeDagpenger.EØS_FERIETILLEGG -> "DPDPASISP1"
}

private fun klassekode(stønadstype: StønadTypeAAP): String = when (stønadstype) {
    StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING -> "AAPUAA"
}

