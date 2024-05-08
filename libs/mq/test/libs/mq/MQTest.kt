package libs.mq

import com.ibm.mq.jms.MQQueue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import javax.jms.TextMessage

class MQTest {
    private val container = MQTestcontainer()
    private val mq = MQ(container.config)
    private val QUEUE_1 = MQQueue("DEV.QUEUE.1")
    private val consumer = Consumer()
    private val producer = MQProducer(mq, QUEUE_1)

    @AfterEach
    fun cleanup() {
        consumer.close()
    }

    @Test
    fun depth() {
        producer.produce("<xml>test1</xml>")
        producer.produce("<xml>test2</xml>")
        producer.produce("<xml>test3</xml>")
        producer.produce("<xml>test4</xml>")

        assertEquals(4, mq.depth(QUEUE_1))
        assertEquals(0, consumer.received.size)

        consumer.start()

        assertEquals(0, mq.depth(QUEUE_1))
        assertEquals(4, consumer.received.size)
    }

    inner class Consumer : MQConsumer(mq, QUEUE_1) {
        internal val received: MutableList<TextMessage> = mutableListOf()

        override fun onMessage(message: TextMessage) {
            received.add(message)
        }

        override fun close() {
            received.clear()
            super.close()
        }
    }
}
