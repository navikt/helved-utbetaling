package overfør

import com.ibm.mq.jms.MQQueue
import libs.mq.MQ
import libs.mq.MQConsumer
import libs.mq.MQProducer
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import overfør.Config
import javax.jms.TextMessage

class MQFake(
    private val config: Config,
    private val mq: MQ = MQ(config.mq)
): AutoCloseable {
    val oppdrag = OppdragListener().apply { start() }

    inner class OppdragListener : MQConsumer(mq, MQQueue(config.oppdrag.sendKø)) {
        private val received: MutableList<TextMessage> = mutableListOf()
        private val mapper = XMLMapper<Oppdrag>()

        fun getReceived() = received.toList()
        fun clearReceived() = received.clear()

        override fun onMessage(message: TextMessage) {
            received.add(message)

            // val oppdrag = mapper.readValue(message.text).apply {
            //     mmel = Mmel().apply {
            //         alvorlighetsgrad = Kvitteringstatus.OK.kode
            //     }
            // }
            //
            // kvitteringsKø.produce(mapper.writeValueAsString(oppdrag))
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

    }
}
