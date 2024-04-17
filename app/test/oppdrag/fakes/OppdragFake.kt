@file:Suppress("NAME_SHADOWING")

package oppdrag.fakes

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import no.trygdeetaten.skjema.oppdrag.Mmel
import oppdrag.Config
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.iverksetting.mq.OppdragXmlMapper
import oppdrag.mq.MQFactory
import javax.jms.*

class OppdragFake(private val config: Config) : AutoCloseable {
    private val oppdragKø = MQQueue(config.oppdrag.sendKø)
    private val kvitteringKø = MQQueue(config.oppdrag.kvitteringsKø)
    private val avstemmingKø = MQQueue(config.avstemming.utKø)

    val sendKøListener = SendKøListener(mutableListOf())
    val avstemmingKøListener = AvstemmingKøListener(mutableListOf())

    inner class SendKøListener(private val received: MutableList<TextMessage>) : MessageListener {
        fun reset() = received.clear()
        fun getReceived() = received.toList()

        override fun onMessage(message: Message) {
            received.add(message as TextMessage)

            val oppdrag = OppdragXmlMapper
                .tilOppdrag(message.text)
                .apply { mmel = Mmel().apply { alvorlighetsgrad = Kvitteringstatus.OK.kode } }

            session.createProducer(kvitteringKø).use { producer ->
                val xml = OppdragXmlMapper.tilXml(oppdrag)
                val msg = session.createTextMessage(xml)
                producer.send(msg)
            }
        }
    }

    inner class AvstemmingKøListener(private val received: MutableList<TextMessage>) : MessageListener {
        fun reset() = received.clear()
        fun getReceived() = received.toList()

        override fun onMessage(message: Message) {
            received.add(message as TextMessage)
        }
    }

    private val factory = MQFactory.new(config.mq)
//    MQConnectionFactory().apply {
//        hostName = config.mq.host
//        port = config.mq.port
//        queueManager = config.mq.manager
//        channel = config.mq.channel
//        transportType = WMQConstants.WMQ_CM_CLIENT
//        setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
//    }

    private val connection: Connection = factory.createConnection(config.mq.username, config.mq.password)
    private val session: Session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE).apply {
        createConsumer(oppdragKø).apply { messageListener = sendKøListener }
        createConsumer(avstemmingKø).apply { messageListener = avstemmingKøListener }
    }

    init {
        connection.start()
    }

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
