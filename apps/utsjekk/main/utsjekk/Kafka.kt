package utsjekk

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.*
import libs.utils.appLog
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.common.utils.Utils
import utsjekk.iverksetting.IverksettingResultatDao
import utsjekk.iverksetting.OppdragResultat
import utsjekk.iverksetting.OppdragStatus
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utbetaling.UtbetalingId
import java.util.*
import kotlin.time.Duration.Companion.hours

object Topics {
    const val NUM_PARTITIONS = 3

    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTp = Topic("helved.dryrun-tp.v1", json<Simulering>())
    val dryrunTs = Topic("helved.dryrun-ts.v1", json<Simulering>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val saker = Topic("helved.saker.v1", jsonjsonSet<SakKey, models.UtbetalingId>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val utbetaling = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val utbetalingAap = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val utbetalingDp = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val utbetalingTp = Topic("helved.utbetalinger-tp.v1", json<TpUtbetaling>())
    val utbetalingTs = Topic("helved.utbetalinger-ts.v1", json<TsDto>())
}

object Tables {
    val dryrunAap = Table(Topics.dryrunAap)
    val dryrunDp = Table(Topics.dryrunDp)
    val dryrunTp = Table(Topics.dryrunTp)
    val dryrunTs = Table(Topics.dryrunTs)
    val saker = Table(Topics.saker)
}

object Stores {
    val dryrunAap = Store(Tables.dryrunAap)
    val dryrunDp = Store(Tables.dryrunDp)
    val dryrunTp = Store(Tables.dryrunTp)
    val dryrunTs = Store(Tables.dryrunTs)
}

fun createTopology(): Topology = topology {
    globalKTable(Tables.dryrunAap, retention = 1.hours)
    globalKTable(Tables.dryrunDp, retention = 1.hours)
    globalKTable(Tables.dryrunTp, retention = 1.hours)
    globalKTable(Tables.dryrunTs, retention = 1.hours)
    consumeStatus()
    utbetalingToSak()
}

data class SakKey(val sakId: SakId, val fagsystem: Fagsystem)

/**
 * Hver gang helved.utbetalinger.v1 blir produsert til
 * akkumulerer vi uids (UtbetalingID) for saken og erstatter aggregatet på helved.saker.v1.
 * Dette gjør at vi kan holde på alle aktive uids for en sakid per fagsystem.
 * Slettede utbetalinger fjernes fra lista.
 * Hvis lista er tom men ikke null betyr det at det ikke er første utbetaling på sak.
 */
fun Topology.utbetalingToSak(): KTable<SakKey, Set<models.UtbetalingId>> {
    val ktable = consume(Topics.utbetaling)
        .rekey { _, utbetaling ->
            val fagsystem = if (utbetaling.fagsystem.isTilleggsstønader()) {
                Fagsystem.TILLEGGSSTØNADER
            } else {
                utbetaling.fagsystem
            }
            SakKey(utbetaling.sakId, fagsystem)
        }
        .groupByKey(Serde.json(), Serde.json(), "utbetalinger-groupby-sakkey")
        .aggregate(Tables.saker) { _, utbetaling, uids ->
            when (utbetaling.action) {
                Action.DELETE -> uids - utbetaling.uid
                else -> uids + utbetaling.uid
            }
        }

    ktable
        .toStream()
        .produce(Topics.saker)

    return ktable
}

fun Topology.consumeStatus() {
    // TODO: utled status fra oppdrag i stedet? Da får vi ikke noe status på topic for disse
    consume(Topics.status)
        .filterKey { uid ->
            try {
                UUID.fromString(uid) != null
            } catch (e: Exception) {
                // noen statuser bruker kafka-key til vedtaksteamene (som har brukt abetal)
                false
            }
        }
        .forEach { key, status ->
            val uid = UtbetalingId(UUID.fromString(key))

            // if (status.status == Status.FEILET) {
            //     SimuleringSubscriptions.complete(key, status)
            // }

            runBlocking {
                withContext(Jdbc.context) {
                    val uDao = transaction {
                        UtbetalingDao.findOrNull(uid, history = true)?.also { dao ->
                            val status = when (status.status) {
                                Status.OK -> utsjekk.utbetaling.Status.OK
                                Status.FEILET -> utsjekk.utbetaling.Status.FEILET_MOT_OPPDRAG
                                Status.HOS_OPPDRAG -> utsjekk.utbetaling.Status.SENDT_TIL_OPPDRAG
                                Status.MOTTATT -> utsjekk.utbetaling.Status.IKKE_PÅBEGYNT
                            }
                            dao.copy(status = status).update(uid)

                            // created_at er lik updated_at om det er første oppdrag
                            if (status == utsjekk.utbetaling.Status.FEILET_MOT_OPPDRAG && dao.created_at.isEqual(dao.updated_at)) {
                                dao.delete(uid)
                            }
                        }
                    }

                    val iDao = transaction {
                        IverksettingResultatDao
                            .select(1) { this.uid = uid }
                            .singleOrNull()?.also { dao ->
                                val status = when (status.status) {
                                    Status.OK -> OppdragStatus.KVITTERT_OK
                                    Status.FEILET -> OppdragStatus.KVITTERT_FUNKSJONELL_FEIL
                                    Status.HOS_OPPDRAG -> OppdragStatus.LAGT_PÅ_KØ
                                    Status.MOTTATT -> OppdragStatus.LAGT_PÅ_KØ
                                }
                                dao.copy(oppdragResultat = OppdragResultat(status)).update(uid)
                            }
                    }

                    if (uDao == null && iDao == null) {
                        appLog.info("Mottok status for key:${key} uid:${uid} som ikke finnes i utsjekk.")
                    }
                }
            }
        }
}


fun partition(key: String): Int {
    val bytes = key.toByteArray()
    val hash = Utils.murmur2(bytes)
    return Utils.toPositive(hash) % Topics.NUM_PARTITIONS
}

