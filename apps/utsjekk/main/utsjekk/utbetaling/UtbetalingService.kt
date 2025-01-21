package utsjekk.utbetaling

import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.task.Tasks
import libs.utils.Result
import libs.utils.onSuccess
import no.nav.utsjekk.kontrakter.felles.objectMapper
import utsjekk.notFound
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
                UtbetalingDao.find(utbetaling.sakId)
                    .map { it.stønad.asFagsystemStr() }
                    .none { it == utbetaling.stønad.asFagsystemStr() }
            }
        }

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
            utbetalingsperioder = utbetaling.perioder.map { periode ->
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = periode.id,
                    vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                    klassekode = klassekode(utbetaling.stønad),
                    fom = periode.fom,
                    tom = periode.tom,
                    sats = periode.beløp,
                    satstype = periode.satstype,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
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
        val existing = dao.data

        existing.validateDiff(utbetaling)

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
            utbetalingsperioder = Utbetalingsperioder.utled(existing, utbetaling)
        )
        return withContext(Jdbc.context) {
            transaction {
                Tasks.create(libs.task.Kind.Utbetaling, oppdrag) {
                    objectMapper.writeValueAsString(it)
                }

                dao.copy(data = utbetaling).update(uid)
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
        val existing = dao.data

        existing.validateDiff(utbetaling) // TODO: valider alt unntatt behandligId?

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
            utbetalingsperioder = listOf(utbetaling.perioder.maxBy { it.fom }.let { sistePeriode ->
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = true, // opphør er alltid en ENDR
                    opphør = Opphør(utbetaling.perioder.minBy { it.fom }.fom),
                    id = sistePeriode.id, // endrer på eksisterende delytelseId
                    vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                    klassekode = klassekode(utbetaling.stønad),
                    fom = sistePeriode.fom,
                    tom = sistePeriode.tom,
                    sats = sistePeriode.beløp,
                    satstype = sistePeriode.satstype,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                )
            })
        )
        return withContext(Jdbc.context) {
            transaction {
                Tasks.create(libs.task.Kind.Utbetaling, oppdrag) {
                    objectMapper.writeValueAsString(it)
                }
                UtbetalingDao.delete(uid) // todo: with history
            }
        }
    }

    suspend fun count(sakId: SakId, stønadstype: Stønadstype): UInt {
        return withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.find(sakId)
                    .map { it.stønad.asFagsystemStr() }
                    .count { it == stønadstype.asFagsystemStr() }
                    .toUInt()
            }
        }
    }
}

internal fun klassekode(stønadstype: Stønadstype): String = when (stønadstype) {
    is StønadTypeDagpenger -> klassekode(stønadstype)
    is StønadTypeTilleggsstønader -> klassekode(stønadstype)
    is StønadTypeTiltakspenger -> klassekode(stønadstype)
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

