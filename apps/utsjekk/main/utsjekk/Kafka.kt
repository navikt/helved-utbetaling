package utsjekk

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.kafka.*
import libs.utils.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import models.*
import models.StatusReply
import models.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.common.utils.Utils
import utsjekk.iverksetting.OppdragResultat
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.simulering.SimuleringSubscriptions
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utbetaling.UtbetalingId

object Topics {
    const val NUM_PARTITIONS = 3

    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val utbetalingDp = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val utbetalingAap = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val utbetalingTs = Topic("helved.utbetalinger-ts.v1", json<TsUtbetaling>())
    val utbetalingTp = Topic("helved.utbetalinger-tp.v1", json<TpUtbetaling>())
    val utbetaling = Topic("helved.utbetalinger.v1", json<Utbetaling>())
}

fun createTopology(abetalClient: AbetalClient): Topology = topology {
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

            if (status.status == Status.FEILET) {
                SimuleringSubscriptions.complete(key, status)
            }

            runBlocking {
                withContext(Jdbc.context) {
                    val uDao = transaction {
                        // Hvis en UID ikke finnes her, kan det hende den er i abetal (helved.utbetaling.v1)
                        // TODO: er alltid de som er null irrelevante?
                        UtbetalingDao.findOrNull(uid, history = true)?.also { dao ->
                            val status = when (status.status) {
                                Status.OK -> utsjekk.utbetaling.Status.OK
                                Status.FEILET -> utsjekk.utbetaling.Status.FEILET_MOT_OPPDRAG
                                Status.HOS_OPPDRAG -> utsjekk.utbetaling.Status.SENDT_TIL_OPPDRAG
                                Status.MOTTATT -> utsjekk.utbetaling.Status.IKKE_PÅBEGYNT
                            }
                            dao.copy(status = status).update(uid)

                            if (status == utsjekk.utbetaling.Status.FEILET_MOT_OPPDRAG) {
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
                        // Sjekk om UID finnes i abetal
                        if (abetalClient.exists(uid)) {
                            appLog.info("Mottok status for uid=${uid.id} som håndteres av abetal. Status: $status")
                        } else {
                            appLog.warn("Både db-tabell utbetaling og iverksettingsresultat mangler rad med uid ${uid.id}. UID finnes heller ikke i abetal. Status: $status")
                        }
                    }
                }
            }
        }
    consume(Topics.dryrunDp)
        .forEach { key, dto ->
            SimuleringSubscriptions.complete(key, dto)
        }
}

fun partition(key: String): Int {
    val bytes = key.toByteArray()
    val hash = Utils.murmur2(bytes)
    return Utils.toPositive(hash) % Topics.NUM_PARTITIONS
}

