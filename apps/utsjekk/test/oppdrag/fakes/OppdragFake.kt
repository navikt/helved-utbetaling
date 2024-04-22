package oppdrag.fakes

import com.ibm.mq.jms.MQQueue
import libs.mq.MQ
import libs.mq.MQConsumer
import libs.mq.MQProducer
import libs.utils.appLog
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.Config
import oppdrag.iverksetting.domene.Kvitteringstatus
import javax.jms.TextMessage

class OppdragFake(private val config: Config) : AutoCloseable {
    private val mq = MQ(config.mq)

    val sendKø = SendKøListener().apply { start() }
    val avstemmingKø = AvstemmingKøListener().apply { start() }

    /**
     * Oppdrag sin send-kø må svare med en faked kvittering
     */
    inner class SendKøListener : MQConsumer(mq, MQQueue(config.oppdrag.sendKø)) {
        private val received: MutableList<TextMessage> = mutableListOf()
        private val mapper = XMLMapper<Oppdrag>()
        private val kvitteringQueue = MQQueue(config.oppdrag.kvitteringsKø)
        private val producer = MQProducer(mq, kvitteringQueue)

        fun getReceived() = received.toList()
        fun clearReceived() = received.clear()

        override fun onMessage(message: TextMessage) {
            received.add(message)

            val oppdrag = mapper.readValue(message.text).apply {
                mmel = Mmel().apply {
                    alvorlighetsgrad = Kvitteringstatus.OK.kode
                }
            }

            producer.produce(mapper.writeValueAsString(oppdrag))
        }
    }

    /**
     * Avstemming-køen må bli verifisert i bruk ved grensesnittavstemming.
     */
    inner class AvstemmingKøListener : MQConsumer(mq, MQQueue(config.avstemming.utKø)) {
        private val received: MutableList<TextMessage> = mutableListOf()

        fun getReceived() = received.toList()
        fun clearReceived() = received.clear()

        override fun onMessage(message: TextMessage) {
            appLog.info("Avstemming mottatt i oppdrag-fake ${message.jmsMessageID}")
            received.add(message)
        }
    }

    /**
     * Create test TextMessages for the output queues in context of a session
     */
    fun createMessage(xml: String): TextMessage {
        return mq.transaction {
            it.createTextMessage(xml)
        }
    }

    override fun close() {
        sendKø.close()
        avstemmingKø.close()
//        fun closeWhenEmpty(listener: MQConsumer) {
//            while (mq.depth(avstemmingQueue) != 0)
//                while (listener.queueDepth() != 0) println("Queue $listener not empty.")
//            listener.close()
//        }
//
//        closeWhenEmpty(sendKø)
//        closeWhenEmpty(avstemmingKø)
    }
}
