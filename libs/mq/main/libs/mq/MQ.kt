package libs.mq

import com.ibm.mq.constants.CMQC
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import libs.utils.appLog
import libs.utils.secureLog
import javax.jms.*

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
    private val queueName: String,
) : MessageListener, ExceptionListener, AutoCloseable {
    private val factory = MQFactory.new(config)

    private val connection = factory.createConnection(config.username, config.password).apply {
        exceptionListener = this@MQConsumer
    }

    private val session = connection.createSession(true, 0).apply {
        createConsumer(MQQueue(queueName)).apply {
            messageListener = this@MQConsumer
        }
    }

    fun queueDepth(): Int = connection.transaction {
        it.createBrowser(it.createQueue(queueName)).use { browsed ->
            browsed.enumeration.toList().size
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

interface MQProducer : CompletionListener {
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
    createSession(true, 0).use { session ->
        runCatching {
            appLog.debug("Session created {}}", session)
            block(session)
        }.onSuccess {
            appLog.debug("Session committed successfully {}", session)
            session.commit()
            session.close()
        }.onFailure {
            appLog.error("Rolling back MQ transaction, please check secureLogs or BOQ (backout queue)")
            secureLog.error("Rolling back MQ transaction", it)
            session.rollback()
            session.close()
        }.getOrThrow()
    }
