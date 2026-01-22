package urskog

import com.ibm.mq.jms.MQQueue
import libs.mq.JMSContextFake
import libs.mq.MQ
import libs.mq.TextMessageFake
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import javax.jms.JMSContext
import javax.jms.TextMessage

class MQFake: MQ {
    val mapper = XMLMapper<Oppdrag>()

    override fun depth(queue: MQQueue): Int = context.received.size
    override fun <T : Any> transaction(block: (JMSContext) -> T): T = block(context)
    override fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T = block()
    override val context = JMSContextFake { sentMessage ->
        val xml = sentMessage.text
        val oppdrag = mapper.readValue(xml)
        val kvittert = oppdrag.apply {
            mmel = Mmel().apply {
                alvorlighetsgrad = "00"
            }
        }
        testLog.info("svarer med mmel for ${xml.hashCode()}")
        TextMessageFake(mapper.writeValueAsString(kvittert))
    }

    fun reset() {
        context.received.clear()
    }
    fun sent() = context.received.map {
        mapper.readValue((it as TextMessage).text)
    }
} 

fun MQ.textMessage(xml: String): TextMessage {
    return transaction {
        it.createTextMessage(xml)
    }
}

