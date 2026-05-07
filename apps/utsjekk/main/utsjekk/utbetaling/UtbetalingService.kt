package utsjekk.utbetaling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.Err
import libs.utils.Result
import libs.kafka.KafkaProducer
import models.locked
import models.notFound
import utsjekk.*
import utsjekk.utbetaling.UtbetalingOppdragService
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class UtbetalingService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag> ,
    private val jdbcCtx: CoroutineDatasource,
) {

    /**
     * Legg til nytt utbetalingsoppdrag.
     */
    suspend fun create(uid: UtbetalingId, utbetaling: Utbetaling): Result<Unit, DatabaseError> {
        // TODO: finnes det noe fra før dersom det er sendt inn 1 periode som senere har blitt slettet/annulert/opphørt?
        val finnesFraFør = withContext(jdbcCtx) {
            transaction {
                UtbetalingDao.findOrNull(uid) != null
            }
        }

        if (finnesFraFør) {
            return Err(DatabaseError.Conflict)
        }

        val erFørsteUtbetalingPåSak = utbetaling.erFørsteUtbetaling ?: withContext(jdbcCtx) {
            transaction {
                UtbetalingDao.find(utbetaling.sakId, history = true)
                    .map { it.stønad.asFagsystemStr() }
                    .none { it == utbetaling.stønad.asFagsystemStr() }
            }
        }

        // TODO: Avvent118
        val oppdrag = UtbetalingOppdragService.opprett(utbetaling, erFørsteUtbetalingPåSak)
        oppdragProducer.send(uid.id.toString(), oppdrag, partition(uid.id.toString()))

        return withContext(jdbcCtx) {
            transaction {
                UtbetalingDao(utbetaling, Status.IKKE_PÅBEGYNT).insert(uid)
            }
        }
    }

    /**
     * Hent eksisterende utbetalingsoppdrag
     */
    suspend fun read(uid: UtbetalingId): Utbetaling? {
        return withContext(jdbcCtx) {
            transaction {
                UtbetalingDao.findOrNull(uid)?.data
            }
        }
    }

    /**
     * Hent eksisterende utbetalingsoppdrag
     */
    suspend fun status(uid: UtbetalingId): Status {
        return withContext(jdbcCtx) {
            transaction {
                UtbetalingDao.findOrNull(uid, history = true)?.status
                    ?: notFound("Fant ikke status for utbetaling med uid $uid")
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
        val dao = withContext(jdbcCtx) {
            transaction {
                UtbetalingDao.findOrNull(uid) ?: notFound("Fant ikke utbetaling med uid $uid")
            }
        }

        if (dao.status in setOf(Status.IKKE_PÅBEGYNT, Status.SENDT_TIL_OPPDRAG)) {
            locked("Utbetalingen har et pågående oppdrag, vent til dette er ferdig")
        }

        // The failed oppdrag never took effect at OS, use the last OK state
        val existing = if (dao.status == Status.FEILET_MOT_OPPDRAG) {
            val lastOk = withContext(jdbcCtx) {
                transaction { UtbetalingDao.findLastOk(uid) }
            }?.data
            // sistePeriode may be null on legacy rows created before the field was added
            lastOk?.copy(sistePeriode = lastOk.sistePeriode ?: dao.data.sistePeriode)
                ?: dao.data
        } else {
            dao.data
        }

        existing.validateLockedFields(utbetaling)
        existing.validateMinimumChanges(utbetaling)

        val oppdrag = UtbetalingOppdragService.update(utbetaling, existing)
        oppdragProducer.send(uid.id.toString(), oppdrag, partition(uid.id.toString()))

        return withContext(jdbcCtx) {
            transaction {
                val sisteLinje = oppdrag.oppdrag110.oppdragsLinje150s.last()
                val newLastPeriodeId = PeriodeId.decode(sisteLinje.delytelseId)
                val sistePeriode = Utbetalingsperiode(
                    fom = sisteLinje.datoVedtakFom.toLocalDate(),
                    tom = sisteLinje.datoVedtakTom.toLocalDate(),
                    beløp = sisteLinje.sats.toLong().toUInt(),
                    fastsattDagsats = sisteLinje.vedtakssats157?.vedtakssats?.toLong()?.toUInt(),
                )
                UtbetalingDao(data = utbetaling.copy(lastPeriodeId = newLastPeriodeId, sistePeriode = sistePeriode)).insert(uid)
            }
        }
    }

    suspend fun updateAvvent(uid: UtbetalingId, request: FeilregistrerAvventRequest) {
        val oppdrag = UtbetalingOppdragService.avvent(request)
        withContext(Dispatchers.IO) {
            oppdragProducer.send(
                key = uid.id.toString(),
                value = oppdrag,
                partition = partition(uid.id.toString()),
                headers = mapOf("source" to "utsjekk-avvent"),
            )
        }
    }

    /**
     * Slett en utbetalingsperiode (opphør hele perioden).
     */
    suspend fun delete(uid: UtbetalingId, utbetaling: Utbetaling): Result<Unit, DatabaseError> {
        val dao = withContext(jdbcCtx) {
            transaction {
                UtbetalingDao.findOrNull(uid) ?: notFound("Fant ikke utbetaling med uid $uid")
            }
        }

        if (dao.status in setOf(Status.IKKE_PÅBEGYNT, Status.SENDT_TIL_OPPDRAG)) {
            locked("utbetalingen har et pågående oppdrag, vent til dette er ferdig")
        }

        // The failed oppdrag never took effect at OS, use the last OK state
        val existing = if (dao.status == Status.FEILET_MOT_OPPDRAG) {
            val lastOk = withContext(jdbcCtx) {
                transaction { UtbetalingDao.findLastOk(uid) }
            }?.data
            // sistePeriode may be null on legacy rows created before the field was added
            lastOk?.copy(sistePeriode = lastOk.sistePeriode ?: dao.data.sistePeriode)
                ?: dao.data
        } else {
            dao.data
        }

        existing.validateLockedFields(utbetaling)
        existing.validateEqualityOnDelete(utbetaling)

        val oppdrag = UtbetalingOppdragService.delete(utbetaling, existing)
        oppdragProducer.send(uid.id.toString(), oppdrag, partition(uid.id.toString()))

        return withContext(jdbcCtx) {
            transaction {
                val sisteLinje = oppdrag.oppdrag110.oppdragsLinje150s.last()
                val newLastPeriodeId = PeriodeId.decode(sisteLinje.delytelseId)
                val sistePeriode = Utbetalingsperiode(
                    fom = sisteLinje.datoVedtakFom.toLocalDate(),
                    tom = sisteLinje.datoVedtakTom.toLocalDate(),
                    beløp = sisteLinje.sats.toLong().toUInt(),
                    fastsattDagsats = sisteLinje.vedtakssats157?.vedtakssats?.toLong()?.toUInt(),
                )
                dao.copy(data = existing.copy(lastPeriodeId = newLastPeriodeId, sistePeriode = sistePeriode)).delete(uid)
            }
        }
    }

    suspend fun lastOrNull(uid: UtbetalingId): Utbetaling? {
        return withContext(jdbcCtx) {
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
    StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING_BARN -> "TPBTAF"
    StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING_BARN -> "TPBTARREHABAGDAG"
    StønadTypeTiltakspenger.ARBEIDSTRENING_BARN -> "TPBTATTILT"
    StønadTypeTiltakspenger.AVKLARING_BARN -> "TPBTAAGR"
    StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB_BARN -> "TPBTDJK"
    StønadTypeTiltakspenger.ENKELTPLASS_AMO_BARN -> "TPBTEPAMO"
    StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN -> "TPBTEPVGSHOY"
    StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN -> "TPBTFLV"
    StønadTypeTiltakspenger.GRUPPE_AMO_BARN -> "TPBTGRAMO"
    StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN -> "TPBTGRVGSHOY"
    StønadTypeTiltakspenger.HØYERE_UTDANNING_BARN -> "TPBTHOYUTD"
    StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE_BARN -> "TPBTIPS"
    StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG_BARN -> "TPBTIPSUNG"
    StønadTypeTiltakspenger.JOBBKLUBB_BARN -> "TPBTJK2009"
    StønadTypeTiltakspenger.OPPFØLGING_BARN -> "TPBTOPPFAGR"
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV_BARN -> "TPBTUAOPPFL"
    StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN -> "TPBTUOPPFOPPL"
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
    StønadTypeDagpenger.DAGPENGER -> "DAGPENGER"
    StønadTypeDagpenger.DAGPENGERFERIE -> "DAGPENGERFERIE"
}

private fun klassekode(stønadstype: StønadTypeAAP): String = when (stønadstype) {
    StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING -> "AAPOR"
}

private fun klassekode(stønadstype: StønadTypeHistorisk): String = when (stønadstype) {
    StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER -> "HJRIM"
}

