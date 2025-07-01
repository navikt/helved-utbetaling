package libs.mq

import com.ibm.mq.constants.CMQC
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import libs.tracing.*
import libs.utils.logger
import libs.utils.secureLog
import java.util.*
import java.time.LocalDateTime
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.MessageListener
import javax.jms.TextMessage
import javax.jms.ExceptionListener
import javax.jms.JMSException
import javax.jms.JMSConsumer

val mqLog = logger("mq")

interface MQProducer {
    fun produce(message: String, config: JMSProducer.() -> Unit = {}): String
}

class DefaultMQProducer(
    private val mq: MQ,
    private val queue: MQQueue,
) : MQProducer {
    override fun produce(
        message: String,
        config: JMSProducer.() -> Unit,
    ): String {
        mqLog.info("Producing message on $queue")
        return mq.transaction { ctx ->
            ctx.clientID = UUID.randomUUID().toString()
            val producer = ctx.createProducer().apply(config)
            val message = ctx.createTextMessage(message)
            fun spanBuilder(builder: SpanBuilder): SpanBuilder {
                return builder
                    .setAttribute("messaging.system", "ibmmq")
                    .setAttribute("messaging.destination", queue.baseQueueName)
                    .setAttribute("messaging.operation", "send")
            }

            Tracing.startSpan(
                "mq.produce",
                ::spanBuilder,
                {
                    producer.send(queue, message)
                    message.jmsCorrelationID = message.jmsMessageID
                    Tracing.storeContext(message.jmsMessageID)
                    it.setAttribute("messaging.message_id", message.jmsMessageID)
                })
            message.jmsMessageID
        }
    }
}

interface MQConsumer : AutoCloseable, ExceptionListener {
    //fun onMessage(message: TextMessage)
    fun start()
}

open class DefaultMQConsumer(
    private val mq: MQ,
    private val queue: MQQueue,
    private val onMessage: (TextMessage) -> Unit
) : MQConsumer {
    private var context = createContext()
    private var consumer = createConsumer()

    private fun createContext(): JMSContext {
        return mq.context.apply {
            autoStart = false
            exceptionListener = this@DefaultMQConsumer
        }
    }

    private fun createConsumer(): JMSConsumer {
        return context.createConsumer(queue).apply {
            messageListener = MessageListener {
                val message = it as TextMessage
                mqLog.info("Consuming message on ${queue.baseQueueName}")
                mq.transacted(context) {
                    val context = Tracing.restoreContext(it.jmsCorrelationID)
                    fun spanBuilder(builder: SpanBuilder): SpanBuilder {
                        return builder.setAttribute("messaging.system", "ibmmq")
                            .setAttribute("messaging.destination", queue.baseQueueName)
                            .setAttribute("messaging.destination_kind", "queue")
                            .setAttribute("messaging.message_id", message.jmsMessageID)
                            .setAttribute("messaging.operation", "receive")
                            .setParent(context)
                    }
                    Tracing.startSpan(
                        queue.baseQueueName,
                        ::spanBuilder,
                    ) { span ->
                        try {
                            span.addEvent("message received")
                            onMessage(message)
                            span.addEvent("message processed")
                        } catch (e: Exception) {
                            span.recordException(e)
                            span.setStatus(StatusCode.ERROR)
                            throw e
                        }
                    }
                }
            }
        }
    }

    override fun onException(exception: JMSException) {
        mqLog.error("Connection exception occured. Reconnecting ${queue.baseQueueName}...", exception)
        reconnect()
    }

    private var lastReconnect = LocalDateTime.now()

    @Synchronized
    fun reconnect() {
        if (lastReconnect.plusMinutes(1).isAfter(LocalDateTime.now())) return
        try {
            runCatching {
                close() // we dont care if this fails (maybe already closed)
            }
            context = createContext()
            consumer = createConsumer()
            start()
            mqLog.info("Sucessfully reconnected consumer ${queue.baseQueueName}")
        } catch (e: Exception) {
            mqLog.error("Failed to reconnect", e)
        } finally {
            lastReconnect = LocalDateTime.now()
        }
    }

    override fun start() {
        context.start()
    }

    override fun close() {
        consumer.close()
        context.close()
    }
}

interface MQ {
    fun depth(queue: MQQueue): Int
    fun <T : Any> transaction(block: (JMSContext) -> T): T
    fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T
    val context: JMSContext
}

class DefaultMQ(private val config: MQConfig) : MQ {
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
        // clientReconnectOptions = WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR // try to reconnect to the same queuemanager
        // clientReconnectTimeout = 600 // reconnection attempts for 10 minutes
    }

    override val context: JMSContext
        get() = factory.createContext(
            config.username,
            config.password,
            JMSContext.SESSION_TRANSACTED
        )

    override fun depth(queue: MQQueue): Int {
        mqLog.debug("Checking queue depth for ${queue.baseQueueName}")
        return transaction { ctx ->
            ctx.createBrowser(queue).use { browse ->
                browse.enumeration.toList().size
            }
        }
    }

    override fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T {
        mqLog.debug("MQ transaction created {}", ctx)

        val result = runCatching {
            block()
        }.onSuccess {
            ctx.commit()
            mqLog.debug("MQ transaction committed {}", ctx)
        }.onFailure {
            ctx.rollback()
            mqLog.error("MQ transaction rolled back {}, please check secureLogs or BOQ (backout queue)", ctx)
            secureLog.error("MQ transaction rolled back {}", ctx, it)
        }

        return result.getOrThrow()
    }

    override fun <T : Any> transaction(block: (JMSContext) -> T): T =
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
