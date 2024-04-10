package oppdrag

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import no.trygdeetaten.skjema.oppdrag.Mmel
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.iverksetting.mq.OppdragXmlMapper
import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage

class MQFake(private val config: OppdragConfig) : MessageListener {
    private val request = MQQueue(config.sendKø)
    private val reply = MQQueue(config.kvitteringsKø)

    private val factory = MQConnectionFactory().apply {
        hostName = config.mq.host
        port = config.mq.port
        queueManager = config.mq.manager
        channel = config.mq.channel
        transportType = WMQConstants.WMQ_CM_CLIENT
        setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
    }

    private lateinit var session: Session

    fun start() {
        val connection = factory.createConnection(config.mq.username, config.mq.password)
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE).apply {
            createConsumer(request).apply {
                messageListener = this@MQFake
            }
        }
        connection.start()
    }

    fun createMessage(xml: String): TextMessage {
        factory.createConnection(config.mq.username, config.mq.password).use {
            it.createSession(false, Session.AUTO_ACKNOWLEDGE).use { session ->
                return session.createTextMessage(xml)
            }
        }
    }

    override fun onMessage(message: Message?) {
        val mmel = Mmel().apply {
            this.alvorlighetsgrad = Kvitteringstatus.OK.kode
        }

        val oppdrag = OppdragXmlMapper.tilOppdrag((message as TextMessage).text).apply {
            this.mmel = mmel
        }

        val kvitteringXml = OppdragXmlMapper.tilXml(oppdrag)

        session.createProducer(reply).use { producer ->
            val msg = session.createTextMessage(kvitteringXml)
            producer.send(msg)
        }
    }
}
