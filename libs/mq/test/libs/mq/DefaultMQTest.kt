package libs.mq

import com.ibm.mq.jms.MQQueue
import javax.jms.Message
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
