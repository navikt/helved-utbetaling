package utsjekk.utbetaling

import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.utils.Err
import libs.utils.Result
import libs.kafka.KafkaProducer
import utsjekk.*
import utsjekk.utbetaling.abetal.OppdragService
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class UtbetalingService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag> ,
) {

    /**
     * Legg til nytt utbetalingsoppdrag.
     */
    suspend fun create(uid: UtbetalingId, utbetaling: Utbetaling): Result<Unit, DatabaseError> {
        // TODO: finnes det noe fra før dersom det er sendt inn 1 periode som senere har blitt slettet/annulert/opphørt?
        val finnesFraFør = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid) != null
            }
        }

        if (finnesFraFør) {
            return Err(DatabaseError.Conflict)
        }

        val erFørsteUtbetalingPåSak = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.find(utbetaling.sakId, history = true)
                    .map { it.stønad.asFagsystemStr() }
                    .none { it == utbetaling.stønad.asFagsystemStr() }
            }
        }

        // TODO: Avvent118
        val oppdrag = OppdragService.opprett(utbetaling, erFørsteUtbetalingPåSak)
        oppdragProducer.send(uid.id.toString(), oppdrag, partition(uid.id.toString()))

        return withContext(Jdbc.context) {
            transaction {
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
                UtbetalingDao.findOrNull(uid, history = true)?.status ?: notFound("status for utbetaling", "uid")
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

        if (dao.status != Status.OK) {
            locked("utbetalingen har et pågående oppdrag, vent til dette er ferdig")
        }

        val existing = dao.data

        existing.validateLockedFields(utbetaling)
        existing.validateMinimumChanges(utbetaling)

        val oppdrag = OppdragService.update(utbetaling, existing)
        oppdragProducer.send(uid.id.toString(), oppdrag, partition(uid.id.toString()))

        return withContext(Jdbc.context) {
            transaction {
                // val newLastPeriodeId = PeriodeId.decode(oppdrag.utbetalingsperioder.last().id)
                val newLastPeriodeId =
                    PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId) // TODO: riktig?
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

        if (dao.status != Status.OK) {
            locked("utbetalingen har et pågående oppdrag, vent til dette er ferdig")
        }

        val existing = dao.data

        existing.validateLockedFields(utbetaling)
        existing.validateEqualityOnDelete(utbetaling)

        val oppdrag = OppdragService.delete(utbetaling, existing)
        oppdragProducer.send(uid.id.toString(), oppdrag, partition(uid.id.toString()))

        return withContext(Jdbc.context) {
            transaction {
                val newLastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                dao.copy(data = existing.copy(lastPeriodeId = newLastPeriodeId)).delete(uid)
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
    is StønadTypeHistorisk -> klassekode(stønadstype)
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

private fun klassekode(stønadstype: StønadTypeHistorisk): String = when (stønadstype) {
    StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER -> "HJRIM"
}

