package libs.mq

import com.ibm.mq.jms.MQQueue
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.jms.Destination
import javax.jms.JMSContext
import javax.jms.Message
import javax.jms.TextMessage

class DefaultMQTest {
    private val mq = FakeMQ()
    private val replyQueue = MQQueue("reply")
    private val consumer = FakeConsumerService(mq, replyQueue)
    private val producer = DefaultMQProducer(mq, replyQueue)

    @AfterEach
    fun cleanup() {
        consumer.close()
    }

    @Test
    fun depth() {
        assertEquals(0, FakeConsumerService.received.size)

        mq.fakeReply(replyQueue) {
            it
        }

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

class FakeMQ: MQ {
    override fun depth(queue: MQQueue): Int = context.sent[queue]?.size ?: -1
    override fun <T : Any> transaction(block: (JMSContext) -> T): T = block(context)
    override fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T = block()
    override val context = JMSContextFake()

    fun reset() {
        context.sent.forEach { it.value.clear() }
    }

    fun fakeReply(dest: Destination, reply: (TextMessage) -> TextMessage) {
        context.fakeReply(dest, reply)
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
