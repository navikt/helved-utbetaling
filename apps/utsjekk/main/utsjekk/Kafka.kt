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
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utbetaling.UtbetalingId

object Topics {
    const val NUM_PARTITIONS = 3

    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val status = Topic("helved.status.v1", json<StatusReply>())
}

fun createTopology(): Topology = topology {
    consume(Topics.status)
        .filterKey { uid ->
            try {
                UUID.fromString(uid) != null
            } catch (e: Exception) {
                // noen statuser bruker kafka-key til vedtaksteamene (som har brukt abetal)
                false
            }
        }
        .forEach { uid, status ->
            val uid = UtbetalingId(UUID.fromString(uid))
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

                            if(status == utsjekk.utbetaling.Status.FEILET_MOT_OPPDRAG) {
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
                        appLog.warn("Både db-tabell utbetaling og iverksettingsresultat mangler rad med uid ${uid.id}. Status: $status")
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

