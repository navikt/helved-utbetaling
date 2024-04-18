package libs.mq

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.jms.Connection
import javax.jms.JMSException
import javax.jms.Message

private const val QUEUE_1 = "DEV.QUEUE.1"
private const val QUEUE_2 = "DEV.QUEUE.2"

class TestMQ {
    private val mq = MQTestContainer()
    private val producer = Producer(mq.config, QUEUE_1)
    private val consumer = Consumer(mq.config, QUEUE_1)

    @AfterEach
    fun cleanup() {
        consumer.received.clear()
    }

//    @Test
//    fun `transaction rollback`() {
//        val factory = MQFactory.new(mq.config)
//        consumer.start()
//
//        val msg = assertThrows<IllegalStateException> {
//            factory.createConnection(mq.config.username, mq.config.password).use {
//                it.transaction {
//                    producer.send("data")
//                    error("woops")
//                }
//            }
//        }
//
//        assertEquals("woops", msg.message)
//        assertEquals(0, consumer.depth())
//        assertTrue(consumer.received.isEmpty())
//    }

    @Test
    fun depth() {
        producer.send("<xml>test1</xml>")
        producer.send("<xml>test2</xml>")
        producer.send("<xml>test3</xml>")
        producer.send("<xml>test4</xml>")

        assertEquals(4, consumer.depth())
        assertEquals(0, consumer.received.size)
        consumer.start()

        // give test time to process all 4 messages
        Thread.sleep(50)

        assertEquals(0, consumer.depth())
        assertEquals(4, consumer.received.size)
    }
}

class Consumer(config: MQConfig, queue: String) : MQConsumer(config, queue) {
    val received: MutableList<Message> = mutableListOf()

    override fun onMessage(message: Message) {
        received.add(message)
    }

    override fun onException(exception: JMSException) = throw exception
}

class Producer(private val config: MQConfig, private val queue: String) : MQProducer {
    private val factory = MQFactory.new(config)

    fun send(xml: String) {
        factory.createConnection(config.username, config.password).use {
            send(xml, it)
        }
    }

    override fun send(xml: String, con: Connection) {
        con.transaction { session ->
            session.createProducer(session.createQueue(queue)).use { producer ->
                producer.send(session.createTextMessage(xml))
            }
        }
    }
}
