package libs.mq

import com.ibm.mq.constants.CMQC
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
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

interface MQConsumer : MessageListener, ExceptionListener, AutoCloseable {
    fun start()
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
        session
            .runCatching(block)
            .onSuccess { session.commit() }
            .onFailure { session.rollback() }
            .getOrThrow()
    }
