package oppdrag.fakes

import com.ibm.mq.jms.MQQueue
import libs.mq.MQConsumer
import libs.mq.MQFactory
import libs.mq.MQProducer
import libs.mq.transaction
import libs.utils.appLog
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.Config
import oppdrag.iverksetting.domene.Kvitteringstatus
import java.lang.Exception
import javax.jms.*

class OppdragFake(private val config: Config) : AutoCloseable {
    private val factory = MQFactory.new(config.mq)

    val sendKø = SendKøListener().apply { start() }
    val avstemmingKø = AvstemmingKøListener().apply { start() }

    /**
     * Oppdrag sin send-kø må svare med en faked kvittering
     */
    inner class SendKøListener : MQConsumer(config.mq, config.oppdrag.sendKø), MQProducer {
        private val received: MutableList<TextMessage> = mutableListOf()
        private val mapper = XMLMapper<Oppdrag>()
        private val kvitteringQueue = MQQueue(config.oppdrag.kvitteringsKø)

        fun getReceived() = received.toList()
        fun clearReceived() = received.clear()

        override fun onMessage(message: Message) {
            received.add(message as TextMessage)

            val oppdrag = mapper.readValue(message.text).apply {
                mmel = Mmel().apply {
                    alvorlighetsgrad = Kvitteringstatus.OK.kode
                }
            }

            factory.createConnection(config.mq.username, config.mq.password).use { con ->
                send(mapper.writeValueAsString(oppdrag), con)
            }
        }

        // exception receiving
        override fun onException(exception: JMSException) {
            error("${config.oppdrag.sendKø} feilet med ${exception.message}")
        }

        override fun send(xml: String, con: Connection) {
            con.transaction { session ->
                session.createProducer(kvitteringQueue).use { producer ->
                    producer.send(createMessage(xml), this)
                }
            }
        }

        // completed sending
        override fun onCompletion(message: Message) {
            appLog.info("Kvittering sendt tilbake fra fake til app: $message}")
        }

        // exception sending
        override fun onException(message: Message?, exception: Exception?) {
            error("${config.oppdrag.sendKø} feilet sending av $message")
        }
    }

    /**
     * Avstemming-køen må bli verifisert i bruk ved grensesnittavstemming.
     */
    inner class AvstemmingKøListener : MQConsumer(config.mq, config.avstemming.utKø) {
        private val received: MutableList<TextMessage> = mutableListOf()
        private val avstemmingQueue = MQQueue(config.avstemming.utKø)

        fun getReceived() = received.toList()
        fun clearReceived() = received.clear()

        override fun onMessage(message: Message) {
            appLog.info("Avstemming mottatt i oppdrag-fake ${message.jmsMessageID}")
            received.add(message as TextMessage)
        }

        override fun onException(exception: JMSException) {
            error("$avstemmingQueue feilet med ${exception.message}")
        }
    }

    /**
     * Create test TextMessages for the output queues in context of a session
     */
    fun createMessage(xml: String): TextMessage {
        factory.createConnection(config.mq.username, config.mq.password).use {
            it.createSession(true, 0).use { session ->
                return session.createTextMessage(xml)
            }
        }
    }

    override fun close() {
        fun closeWhenEmpty(listener: MQConsumer) {
            while (listener.queueDepth() != 0) println("Queue $listener not empty.")
            listener.close()
        }

        closeWhenEmpty(sendKø)
        closeWhenEmpty(avstemmingKø)
    }
}
