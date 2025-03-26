package utsjekk

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.kafka.*
import libs.utils.*
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import models.*
import models.StatusReply
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
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
        .forEach { uid, status ->
            val uid = UtbetalingId(UUID.fromString(uid))
            runBlocking {
                withContext(Jdbc.context) {
                    transaction {
                        // TODO: er alltid de som er null irrelevante?
                        UtbetalingDao.findOrNull(uid, history = true)?.let { dao ->
                            val status = when (status.status) {
                                Status.OK -> utsjekk.utbetaling.Status.OK
                                Status.FEILET -> utsjekk.utbetaling.Status.FEILET_MOT_OPPDRAG
                                Status.HOS_OPPDRAG -> utsjekk.utbetaling.Status.SENDT_TIL_OPPDRAG
                                Status.MOTTATT -> utsjekk.utbetaling.Status.IKKE_PÅBEGYNT // TODO: denn må vi sette selv fra utsjekk
                            }
                            dao.copy(status = status).update(uid)
                        }
                    }

                    transaction {
                        IverksettingResultatDao
                            .select(1) { this.uid = uid }
                            .singleOrNull()?.let { dao ->
                                val status = when (status.status) {
                                    Status.OK -> OppdragStatus.KVITTERT_OK
                                    Status.FEILET -> OppdragStatus.KVITTERT_FUNKSJONELL_FEIL
                                    Status.HOS_OPPDRAG -> OppdragStatus.LAGT_PÅ_KØ
                                    Status.MOTTATT -> OppdragStatus.LAGT_PÅ_KØ
                                }
                                dao.copy(oppdragResultat = OppdragResultat(status)).update(uid)
                            }
                    }
                }
            }
        }
}

class OppdragKafkaProducer(config: StreamsConfig, kafka: Streams) : AutoCloseable {
    val producer: Producer<String, Oppdrag> = kafka.createProducer(config, Topics.oppdrag)

    fun produce(key: UtbetalingId, value: Oppdrag) {
        val key = key.id.toString()
        val record = ProducerRecord(Topics.oppdrag.name, partition(key), key, value)
        producer.send(record) { metadata, err ->
            if (err != null) {
                appLog.error("Klarte ikke sende oppdrag til ${Topics.oppdrag.name} ($metadata)")
                secureLog.error("Klarte ikke sende oppdrag til ${Topics.oppdrag.name} ($metadata)", err)
            } else {
                secureLog.trace("Oppdrag produsert for {} til {} ({})", key, { Topics.oppdrag.name }, metadata)
            }
        }.get()
    }

    override fun close() {
        producer.close()
    }

    private fun partition(key: String): Int {
        val bytes = key.toByteArray()
        val hash = Utils.murmur2(bytes)
        return Utils.toPositive(hash) % Topics.NUM_PARTITIONS
    }
}
