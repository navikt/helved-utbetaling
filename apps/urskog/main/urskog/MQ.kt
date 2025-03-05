package urskog

import com.ibm.mq.jms.MQQueue
import javax.jms.TextMessage
import libs.kafka.StateStore
import libs.mq.*
import libs.utils.secureLog
import libs.xml.XMLMapper
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.clients.producer.*
import net.logstash.logback.argument.StructuredArguments.kv

class OppdragMQProducer(
    private val config: Config,
    mq: MQ,
) {
    private val kvitteringQueue = MQQueue(config.oppdrag.kvitteringsKø)
    private val producer = DefaultMQProducer(mq, config.oppdrag.sendKø)
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
    private val keystore: StateStore<OppdragForeignKey, UtbetalingId>,
    mq: MQ,
): AutoCloseable {
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()
    private val consumer = DefaultMQConsumer(mq, MQQueue(config.oppdrag.kvitteringsKø), ::onMessage)

    fun onMessage(message: TextMessage) {
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(message.text))
        Thread.sleep(1000) // TEST: check for race condition
        val fk = OppdragForeignKey.from(kvittering)
        val uid = keystore.getOrNull(fk)
        appLog.info("Mottok kvittering $fk $uid")
        if (uid == null) {
            appLog.error("uid not found for $fk. Kvittering not redirected from MQ to Kafka");
            secureLog.error("$fk: uid not found for. Kvittering not redirected from MQ to Kafka\n${leggTilNamespacePrefiks(message.text)}");
        } else {
            // TODO: må man eksplisitt velge partition, eller vil den resolve likt som kafka-streams?
            val record = ProducerRecord(Topics.kvittering.name, uid.id.toString(), kvittering)
            kvitteringProducer.send(record) { md, err ->
                when (err) {
                    null -> secureLog.trace(
                        "produce ${Topics.kvittering.name}",
                        kv("key", uid.id.toString()),
                        kv("topic", Topics.kvittering.name),
                        kv("partition", md.partition()),
                        kv("offset", md.offset()),
                    ) 
                    else -> secureLog.error("Failed to produce record for $uid")
                }
            }
        }
    }


    fun start() {
        consumer.start()
    }

    override fun close() {
        consumer.close()
    }

    private fun leggTilNamespacePrefiks(xml: String): String {
        return xml
            .replace("<oppdrag xmlns=", "<ns2:oppdrag xmlns:ns2=", ignoreCase = true)
            .replace("</oppdrag>", "</ns2:oppdrag>", ignoreCase = true)
    }
}

data class OppdragForeignKey(
    val fagsystem: Fagsystem,
    val sakId: SakId,
    val behandlingId: BehandlingId? = null,
) {
    companion object {
        fun from(oppdrag: Oppdrag) = OppdragForeignKey(
            fagsystem = Fagsystem.valueOf(oppdrag.oppdrag110.kodeFagomraade),
            sakId = SakId(oppdrag.oppdrag110.fagsystemId), 
            behandlingId = oppdrag.oppdrag110.oppdragsLinje150s?.lastOrNull()?.henvisning?.let(::BehandlingId)
        )

        fun from(utbetaling: Utbetaling) = OppdragForeignKey(
            fagsystem = Fagsystem.from(utbetaling.stønad),
            sakId = utbetaling.sakId,
            behandlingId = utbetaling.behandlingId,
        )
    }
}

