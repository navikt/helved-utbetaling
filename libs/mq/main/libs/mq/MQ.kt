package libs.mq

import com.ibm.mq.constants.CMQC
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import libs.utils.appLog
import libs.utils.secureLog
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.MessageListener
import javax.jms.TextMessage

class MQProducer(
    private val mq: MQ,
    private val queue: MQQueue,
) {
    fun produce(
        message: String,
        config: JMSProducer.() -> Unit = {},
    ) {
        appLog.debug("Producing message on ${queue.baseQueueName}")
        mq.transaction { ctx ->
            val producer = ctx.createProducer().apply(config)
            producer.send(queue, message)
        }
    }
}

internal interface MQListener {
    fun onMessage(message: TextMessage)
}

abstract class MQConsumer(
    private val mq: MQ,
    private val queue: MQQueue,
) : AutoCloseable, MQListener {
    private val context = mq.context.apply {
        autoStart = false
    }

    private val consumer = context.createConsumer(queue).apply {
        messageListener = MessageListener {
            appLog.debug("Consuming message on ${queue.baseQueueName}")
            mq.transacted(context) {
                onMessage(it as TextMessage)
            }
        }
    }

    fun depth(): Int {
        return mq.depth(queue)
    }

    fun start() {
        context.start()
    }

    override fun close() {
        consumer.close()
        context.close()
    }
}

class MQ(private val config: MQConfig) {
    private val factory: MQConnectionFactory = MQConnectionFactory().apply {
        hostName = config.host
        port = config.port
        queueManager = config.manager
        channel = config.channel
        transportType = WMQConstants.WMQ_CM_CLIENT
        ccsid = JmsConstants.CCSID_UTF8
        userAuthenticationMQCSP = true
        setIntProperty(JmsConstants.JMS_IBM_ENCODING, CMQC.MQENC_NATIVE)
        setIntProperty(JmsConstants.JMS_IBM_CHARACTER_SET, JmsConstants.CCSID_UTF8)
    }

    internal val context: JMSContext
        get() = factory.createContext(
            config.username,
            config.password,
            JMSContext.SESSION_TRANSACTED
        )

    fun depth(queue: MQQueue): Int {
        appLog.debug("Checking queue depth for ${queue.baseQueueName}")
        return transaction { ctx ->
            ctx.createBrowser(queue).use { browse ->
                browse.enumeration.toList().size
            }
        }
    }

    internal fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T {
        appLog.debug("MQ transaction created {}", ctx)

        val result = runCatching {
            block()
        }.onSuccess {
            ctx.commit()
            appLog.debug("MQ transaction committed {}", ctx)
        }.onFailure {
            ctx.rollback()
            appLog.error("MQ transaction rolled back {}, please check secureLogs or BOQ (backout queue)", ctx)
            secureLog.error("MQ transaction rolled back {}", ctx, it)
        }

        return result.getOrThrow()
    }

    fun <T : Any> transaction(block: (JMSContext) -> T): T =
        context.use { ctx ->
            transacted(ctx) {
                block(ctx)
            }
        }
}

data class MQConfig(
    val host: String,
    val port: Int,
    val channel: String,
    val manager: String,
    val username: String,
    val password: String,
)
