package urskog

import com.ibm.mq.jms.MQQueue
import javax.jms.TextMessage
import libs.kafka.StateStore
import libs.mq.*
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.clients.producer.*
import urskog.OppdragConfig
import urskog.models.*
import java.util.UUID

class OppdragMQProducer(
    private val config: Config,
    mq: MQ = MQ(config.mq),
) {
    private val oppdragQueue = MQQueue(config.oppdrag.sendKø)
    private val kvitteringQueue = MQQueue(config.oppdrag.kvitteringsKø)
    private val producer = MQProducer(mq, oppdragQueue)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()

    fun send(oppdrag: Oppdrag) {
        val oppdragXml = mapper.writeValueAsString(oppdrag)
        val fk = OppdragForeignKey.from(oppdrag)

        runCatching {
            producer.produce(oppdragXml) {
                jmsReplyTo = kvitteringQueue
            }
            appLog.info("Sender oppdrag $fk")
        }.onFailure {
            appLog.error("Feilet sending av oppdrag $fk")
            secureLog.error("Feilet sending av oppdrag $fk", it)
        }.getOrThrow()
    }
}

class KvitteringMQConsumer(
    private val config: Config,
    private val kvitteringProducer: Producer<String, Oppdrag>,
    private val keystore: StateStore<OppdragForeignKey, UUID>,
    mq: MQ = MQ(config.mq),
): MQConsumer(mq, MQQueue(config.oppdrag.kvitteringsKø)) {
    private val kvitteringQueue = MQQueue(config.oppdrag.kvitteringsKø)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()

    override fun onMessage(message: TextMessage) {
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(message.text))
        val fk = OppdragForeignKey.from(kvittering)
        val uid = keystore.getOrNull(fk)
        appLog.info("Mottok kvittering $fk $uid")
        if (uid == null) {
            appLog.error("uid not found for $fk. Kvittering not redirected from MQ to Kafka");
            secureLog.error("$fk: uid not found for. Kvittering not redirected from MQ to Kafka\n${leggTilNamespacePrefiks(message.text)}");
        } else {
            // TODO: må man eksplisitt velge partition, eller vil den resolve likt som kafka-streams?
            val record = ProducerRecord(Topics.status.name, uid.toString(), kvittering)
            kvitteringProducer.send(record)
        }
    }

    private fun leggTilNamespacePrefiks(xml: String): String {
        return xml
            .replace("<oppdrag xmlns=", "<ns2:oppdrag xmlns:ns2=", ignoreCase = true)
            .replace("</oppdrag>", "</ns2:oppdrag>", ignoreCase = true)
    }
}

data class OppdragForeignKey(
    val fagsystem: Fagsystem,
    val sakId: String,
    val behandlingId: String?,
) {
    companion object {
        fun from(oppdrag: Oppdrag) = OppdragForeignKey(
            fagsystem = Fagsystem.valueOf(oppdrag.oppdrag110.kodeFagomraade),
            sakId = oppdrag.oppdrag110.fagsystemId, 
            behandlingId = oppdrag.oppdrag110.oppdragsLinje150s?.lastOrNull()?.henvisning
        )

        fun from(utbetaling: Utbetaling) = OppdragForeignKey(
            fagsystem = Fagsystem.from(utbetaling.stønad),
            sakId = utbetaling.sakId,
            behandlingId = utbetaling.behandlingId,
        )
    }
}

