package libs.mq

import com.ibm.mq.jms.MQQueue
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.jms.JMSContext
import javax.jms.Message
import javax.jms.TextMessage

class DefaultMQTest {
    private val mq = MQFake()
    private val consumer = FakeConsumerService(mq, MQQueue("reply"))
    private val producer = DefaultMQProducer(mq, MQQueue("request"))

    @AfterEach
    fun cleanup() {
        consumer.close()
    }

    @Test
    fun depth() {
        assertEquals(0, FakeConsumerService.received.size)

        producer.produce("<xml>test1</xml>") {
            jmsReplyTo = MQQueue("reply")
        }
        producer.produce("<xml>test2</xml>")
        {
            jmsReplyTo = MQQueue("reply")
        }
        producer.produce("<xml>test3</xml>")
        {
            jmsReplyTo = MQQueue("reply")
        }
        producer.produce("<xml>test4</xml>")
        {
            jmsReplyTo = MQQueue("reply")
        }

        //consumer.start()

        assertEquals(4, FakeConsumerService.received.size)
    }
}

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
        TextMessageFake(mapper.writeValueAsString(kvittert))
    }

    fun reset() {
        context.received.clear()
    }
    fun sent() = context.received.map {
        mapper.readValue((it as TextMessage).text)
    }
} 

class FakeConsumerService(mq: MQ, queue: MQQueue) : DefaultMQConsumer(
    mq, queue,
    FakeConsumerService::onMessage
), AutoCloseable {
    companion object {
        val received: MutableList<Message> = mutableListOf()

        fun onMessage(message: TextMessage) {
            received.add(message)
        }
    }

    override fun close() {
    }
}
