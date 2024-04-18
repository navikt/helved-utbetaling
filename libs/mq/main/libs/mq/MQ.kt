package libs.mq

import com.ibm.mq.constants.CMQC
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import libs.utils.appLog
import libs.utils.secureLog
import javax.jms.Connection
import javax.jms.ExceptionListener
import javax.jms.MessageListener
import javax.jms.Session

data class MQConfig(
    val host: String,
    val port: Int,
    val channel: String,
    val manager: String,
    val username: String,
    val password: String,
)

abstract class MQConsumer(
    config: MQConfig,
    queue: String,
) : MessageListener, ExceptionListener, AutoCloseable {
    private val factory = MQFactory.new(config)

    private val connection = factory.createConnection(config.username, config.password).apply {
        exceptionListener = this@MQConsumer
    }

    private val session = connection.createSession().apply {
        createConsumer(MQQueue(queue)).apply {
            messageListener = this@MQConsumer
        }
    }

    fun start() {
        connection.start()
    }

    override fun close() {
        session.close()
        connection.close()
    }
}

interface MQProducer {
    fun send(xml: String, con: Connection)
}

object MQFactory {
    fun new(config: MQConfig): MQConnectionFactory =
        MQConnectionFactory().apply {
            hostName = config.host
            port = config.port
            queueManager = config.manager
            channel = config.channel
            transportType = WMQConstants.WMQ_CM_CLIENT
            ccsid = JmsConstants.CCSID_UTF8
            setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
            setIntProperty(JmsConstants.JMS_IBM_ENCODING, CMQC.MQENC_NATIVE)
            setIntProperty(JmsConstants.JMS_IBM_CHARACTER_SET, JmsConstants.CCSID_UTF8)
        }
}

fun <T> Connection.transaction(block: (Session) -> T): T =
    createSession(Session.SESSION_TRANSACTED).use { session ->
        runCatching {
            block(session)
        }.onSuccess {
            session.commit()
        }.onFailure {
            appLog.error("Rolling back MQ transaction, please check secureLogs or BOQ (backout queue)")
            secureLog.error("Rolling back MQ transaction", it)
            session.rollback()
        }.getOrThrow()
    }
