package urskog

import com.ibm.mq.jms.MQQueue
import libs.mq.JMSContextFake
import libs.mq.MQ
import libs.xml.XMLMapper
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import javax.jms.Destination
import javax.jms.JMSContext
import javax.jms.TextMessage

class FakeMQ: MQ {
    override fun depth(queue: MQQueue): Int = context.sent[queue]?.size ?: -1
    override fun <T : Any> transaction(block: (JMSContext) -> T): T = block(context)
    override fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T = block()
    override val context = JMSContextFake()

    /**
     *  fakeReply(TestRuntime.config.oppdrag.sendKø) { msg ->
     *      val oppdrag = mapper.readValue(msg.text)
     *      val response = oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
     *      TextMessageFake(mapper.writeValueAsString(response))
     *  }
     */ 
    fun fakeReply(dest: Destination, reply: (TextMessage) -> TextMessage) {
        context.fakeReply(dest, reply)
    }

    fun reset() {
        context.sent.forEach { it.value.clear() }
    }

    fun sentOppdrag(): List<Oppdrag> {
        val mapper = XMLMapper<Oppdrag>()
        return context.sent[TestRuntime.config.oppdrag.sendKø]
            ?.map { mapper.readValue((it as TextMessage).text) }
            ?: emptyList()
    }

    fun sentAvstemming(): List<Avstemmingsdata> {
        val mapper = XMLMapper<Avstemmingsdata>()
        return context.sent[TestRuntime.config.oppdrag.avstemmingKø]
            ?.map { mapper.readValue((it as TextMessage).text) }
            ?: emptyList()
    }
} 

