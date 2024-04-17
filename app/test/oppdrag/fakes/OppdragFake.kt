package oppdrag.fakes

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import no.trygdeetaten.skjema.oppdrag.Mmel
import oppdrag.Config
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.iverksetting.mq.OppdragXmlMapper
import javax.jms.*

// todo: verifiser at dev.queue.3 blir kalt (avstemming.utkø)
class OppdragFake(private val config: Config) : MessageListener, AutoCloseable {
    private val request = MQQueue(config.oppdrag.sendKø)
    private val reply = MQQueue(config.oppdrag.kvitteringsKø)

    private val factory = MQConnectionFactory().apply {
        hostName = config.mq.host
        port = config.mq.port
        queueManager = config.mq.manager
        channel = config.mq.channel
        transportType = WMQConstants.WMQ_CM_CLIENT
        setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
    }

    private val connection: Connection = factory.createConnection(config.mq.username, config.mq.password)
    private val session: Session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE).apply {
        createConsumer(request).apply {
            messageListener = this@OppdragFake
        }
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

    override fun onMessage(message: Message) {
        val oppdrag = OppdragXmlMapper.tilOppdrag((message as TextMessage).text).apply {
            mmel = Mmel().apply {
                alvorlighetsgrad = Kvitteringstatus.OK.kode
            }
        }

        val kvitteringXml = OppdragXmlMapper.tilXml(oppdrag)

        session.createProducer(reply).use { producer ->
            val msg = session.createTextMessage(kvitteringXml)
            producer.send(msg)
        }
    }

    override fun close() {
        session.close()
        connection.close()
    }
}
