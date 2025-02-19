package urskog

import com.ibm.mq.jms.MQQueue
import libs.mq.MQ
import libs.mq.MQConsumer
import libs.mq.MQProducer
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import urskog.Config
import javax.jms.TextMessage

class URFake(
    private val config: Config,
    private val mq: MQ = MQ(config.mq)
): AutoCloseable {
    val oppdragskø = OppdragListener().apply { start() }

    inner class OppdragListener : MQConsumer(mq, MQQueue(config.oppdrag.sendKø)) {
        private val mapper = XMLMapper<Oppdrag>()

        val received: MutableList<TextMessage> = mutableListOf()

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
