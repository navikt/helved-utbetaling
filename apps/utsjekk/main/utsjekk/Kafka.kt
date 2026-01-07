package utsjekk

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.*
import libs.utils.*
import models.*
import models.StatusReply
import models.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.common.utils.Utils
import utsjekk.iverksetting.OppdragResultat
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utbetaling.UtbetalingId
import kotlin.time.Duration.Companion.hours

object Topics {
    const val NUM_PARTITIONS = 3

    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTs = Topic("helved.dryrun-ts.v1", json<models.v1.Simulering>())
    val utbetalingDp = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val utbetalingAap = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val utbetalingTs = Topic("helved.utbetalinger-ts.v1", json<TsDto>())
    val utbetalingTp = Topic("helved.utbetalinger-tp.v1", json<TpUtbetaling>())
    val utbetaling = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val saker = Topic("helved.saker.v1", jsonjsonSet<SakKey, models.UtbetalingId>())
}

object Tables {
    val dryrunTs = Table(Topics.dryrunTs)
    val dryrunDp = Table(Topics.dryrunDp)
    val saker = Table(Topics.saker)
}

object Stores {
    val dryrunTs = Store(Tables.dryrunTs)
    val dryrunDp = Store(Tables.dryrunDp)
}

fun createTopology(): Topology = topology {
    globalKTable(Tables.dryrunTs, retention = 1.hours)
    globalKTable(Tables.dryrunDp, retention = 1.hours)
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
                        appLog.info("Mottok status $status for uid=${uid.id} som ikke finnes i utsjekk. Denne antas å ligge i abetal")
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

