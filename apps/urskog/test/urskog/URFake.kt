package urskog

import com.ibm.mq.jms.MQQueue
import javax.jms.TextMessage
import libs.mq.*
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.*

class URFake(
    private val config: Config,
    val mq: MQ = MQ(config.mq),
): AutoCloseable {
    private val kvitteringsKø = MQProducer(mq, MQQueue(config.oppdrag.kvitteringsKø))
    private val mapper = XMLMapper<Oppdrag>()
    val oppdragskø = OppdragListener().apply { start() }

    inner class OppdragListener : MQConsumer(mq, MQQueue(config.oppdrag.sendKø)) {
        val received: MutableList<TextMessage> = mutableListOf()

        override fun onMessage(message: TextMessage) {
            received.add(message)
            val oppdrag = mapper.readValue(message.text).apply {
                mmel = Mmel().apply {
                    alvorlighetsgrad = "00" // 00/04/08/12
                }
            }
            kvitteringsKø.produce(mapper.writeValueAsString(oppdrag))
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
        runCatching {
            oppdragskø.close()
        }
    }
}

