package oppdrag.fakes

import com.ibm.mq.jms.MQQueue
import no.trygdeetaten.skjema.oppdrag.Mmel
import oppdrag.Config
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.iverksetting.mq.OppdragXmlMapper
import oppdrag.mq.MQConsumer
import oppdrag.mq.MQFactory
import javax.jms.*

class OppdragFake(private val config: Config) : AutoCloseable {
    val sendKø = SendKøListener(mutableListOf())
    val avstemmingKø = AvstemmingKøListener(mutableListOf())

    private val oppdragQueue = MQQueue(config.oppdrag.sendKø)
    private val kvitteringQueue = MQQueue(config.oppdrag.kvitteringsKø)
    private val avstemmingQueue = MQQueue(config.avstemming.utKø)

    private val factory = MQFactory.new(config.mq)
    private val connection: Connection = factory.createConnection(config.mq.username, config.mq.password)
    private val session: Session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE).apply {
        createConsumer(oppdragQueue).apply { messageListener = sendKø }
        createConsumer(avstemmingQueue).apply { messageListener = avstemmingKø }
    }

    /**
     * Oppdrag sin send-kø må svare med en faked kvittering
     */
    inner class SendKøListener(private val received: MutableList<TextMessage>) : MQConsumer {
        fun getReceived() = received.toList()

        override fun start() = received.clear()
        override fun close() = received.clear()

        override fun onMessage(message: Message) {
            received.add(message as TextMessage)

            val oppdrag = OppdragXmlMapper
                .tilOppdrag(message.text)
                .apply { mmel = Mmel().apply { alvorlighetsgrad = Kvitteringstatus.OK.kode } }

            session.createProducer(kvitteringQueue).use { producer ->
                val xml = OppdragXmlMapper.tilXml(oppdrag)
                val msg = session.createTextMessage(xml)
                producer.send(msg)
            }
        }

        override fun onException(exception: JMSException) {
            error("$oppdragQueue feilet med ${exception.message}")
        }
    }

    /**
     * Avstemming-køen må bli verifisert i bruk ved grensesnittavstemming.
     */
    inner class AvstemmingKøListener(private val received: MutableList<TextMessage>) : MQConsumer {
        fun getReceived() = received.toList()

        override fun start() = received.clear()
        override fun close() = received.clear()

        override fun onMessage(message: Message) {
            received.add(message as TextMessage)
        }

        override fun onException(exception: JMSException) {
            error("$avstemmingQueue feilet med ${exception.message}")
        }
    }

    init {
        connection.start()
    }

    /**
     * Create test TextMessages for the output queues in context of a session
     */
    fun createMessage(xml: String): TextMessage {
        factory.createConnection(config.mq.username, config.mq.password).use {
            it.createSession(false, Session.AUTO_ACKNOWLEDGE).use { session ->
                return session.createTextMessage(xml)
            }
        }
    }

    override fun close() {
        session.close()
        connection.close()
    }
}
