package utsjekk.utbetaling

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import libs.postgres.concurrency.transaction
import libs.postgres.Jdbc
import libs.task.TaskDao
import libs.task.Tasks
import libs.utils.Result
import libs.utils.Ok
import libs.utils.Err
import libs.utils.onSuccess
import no.nav.utsjekk.kontrakter.felles.objectMapper
import kotlinx.coroutines.withContext
import utsjekk.notFound

object UtbetalingService {

    /**
     * Legg til nytt utbetalingsoppdrag.
     */
    suspend fun create(uid: UtbetalingId, utbetaling: Utbetaling): Result<Unit, DatabaseError>  {
        val oppdrag = UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = true, // TODO: må vi gjøre sql select på sakid for fagområde?
            fagsystem = FagsystemDto.from(utbetaling.stønad),
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.periode.betalendeEnhet?.enhet,
            utbetalingsperiode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = false,
                opphør = null,
                id = utbetaling.periode.id, 
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

        return withContext(Jdbc.context) {
            transaction {
                Tasks.create(libs.task.Kind.Utbetaling, oppdrag) {
                    objectMapper.writeValueAsString(it)
                }
                UtbetalingDao(utbetaling).insert(uid)
                    .onSuccess {
                        UtbetalingStatusDao(UtbetalingStatus(status = Status.IKKE_PÅBEGYNT)).insert(uid)
                    }
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
    suspend fun status(uid: UtbetalingId): UtbetalingStatus {
        return withContext(Jdbc.context) {
            transaction {
                UtbetalingStatusDao.findOrNull(uid)?.data ?: notFound("status for utbetaling", "uid")
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
        val utbetaling = utbetaling.copy(periode = utbetaling.periode.copy(id = existing.periode.id))
        existing.validateDiff(utbetaling)
        val oppdrag = UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = false, // TODO: hvis man endrer på førsteUtbetalingPåSak, er denne true eller false da?
            fagsystem = FagsystemDto.from(utbetaling.stønad),
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.periode.betalendeEnhet?.enhet,
            utbetalingsperiode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = true,
                opphør = null,
                id = existing.periode.id, // TODO hva skal denne væer 
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
        val utbetaling = utbetaling.copy(periode = utbetaling.periode.copy(id = existing.periode.id))
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
            brukersNavKontor = utbetaling.periode.betalendeEnhet?.enhet,
            utbetalingsperiode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = true, // opphør er alltid en ENDR
                opphør = Opphør(utbetaling.periode.fom),
                id = existing.periode.id, // endrer på eksisterende delytelseId 
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
        return withContext(Jdbc.context) {
            transaction {
                Tasks.create(libs.task.Kind.Utbetaling, oppdrag) {
                    objectMapper.writeValueAsString(it)
                }
                UtbetalingDao.delete(uid) // todo: with history
            }
        }
    }
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
