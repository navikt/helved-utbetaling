package libs.mq

import com.ibm.mq.jms.MQQueue
import java.io.Serializable
import java.util.*
import javax.jms.BytesMessage
import javax.jms.CompletionListener
import javax.jms.ConnectionMetaData
import javax.jms.Destination
import javax.jms.ExceptionListener
import javax.jms.JMSConsumer
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.MapMessage
import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.ObjectMessage
import javax.jms.Queue
import javax.jms.QueueBrowser
import javax.jms.StreamMessage
import javax.jms.TemporaryQueue
import javax.jms.TemporaryTopic
import javax.jms.TextMessage
import javax.jms.Topic

class MQFake(override val context: JMSContext = JMSContextFake()) : MQ {
    override fun depth(queue: MQQueue): Int = TODO("fake")

    override fun <T : Any> transaction(block: (JMSContext) -> T): T {
        return block(context)
    }

    override fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T {
        return block()
    }

}

class JMSContextFake(private val onReplyTo: (TextMessage) -> TextMessage = { it }) : JMSContext {
    val received: MutableList<Message> = mutableListOf()
    var replyTo: String? = null
    lateinit var consumer: JMSConsumer

    override fun createConsumer(p0: Destination): JMSConsumer {
        consumer = JMSConsumerFake()
        return consumer
    }

    override fun close() = TODO("fake")
    override fun createContext(p0: Int): JMSContext = TODO("fake")
    override fun createProducer(): JMSProducer = JMSProducerFake()
    override fun getClientID(): String = TODO("fake")
    override fun setClientID(p0: String?) {}
    override fun getMetaData(): ConnectionMetaData = TODO("fake")
    override fun getExceptionListener(): ExceptionListener = TODO("fake")
    override fun setExceptionListener(p0: ExceptionListener?) {}
    override fun start() {}
    override fun stop() = TODO("fake")
    override fun setAutoStart(p0: Boolean) {}
    override fun getAutoStart(): Boolean = TODO("fake")
    override fun createBytesMessage(): BytesMessage = TODO("fake")
    override fun createMapMessage(): MapMessage = TODO("fake")
    override fun createMessage(): Message = TODO("fake")
    override fun createObjectMessage(): ObjectMessage = TODO("fake")
    override fun createObjectMessage(p0: Serializable?): ObjectMessage = TODO("fake")
    override fun createStreamMessage(): StreamMessage = TODO("fake")
    override fun createTextMessage(): TextMessage = TODO("fake")
    override fun createTextMessage(p0: String): TextMessage = TextMessageFake(p0)
    override fun getTransacted(): Boolean = TODO("fake")
    override fun getSessionMode(): Int = TODO("fake")
    override fun commit() = TODO("fake")
    override fun rollback() = TODO("fake")
    override fun recover() = TODO("fake")
    override fun createConsumer(p0: Destination?, p1: String?): JMSConsumer = TODO("fake")
    override fun createConsumer(p0: Destination?, p1: String?, p2: Boolean): JMSConsumer = TODO("fake")
    override fun createQueue(p0: String?): Queue = TODO("fake")
    override fun createTopic(p0: String?): Topic = TODO("fake")
    override fun createDurableConsumer(p0: Topic?, p1: String?): JMSConsumer = TODO("fake")
    override fun createDurableConsumer(p0: Topic?, p1: String?, p2: String?, p3: Boolean): JMSConsumer = TODO("fake")
    override fun createSharedDurableConsumer(p0: Topic?, p1: String?): JMSConsumer = TODO("fake")
    override fun createSharedDurableConsumer(p0: Topic?, p1: String?, p2: String?): JMSConsumer = TODO("fake")
    override fun createSharedConsumer(p0: Topic?, p1: String?): JMSConsumer = TODO("fake")
    override fun createSharedConsumer(p0: Topic?, p1: String?, p2: String?): JMSConsumer = TODO("fake")
    override fun createBrowser(p0: Queue?): QueueBrowser = TODO("fake")
    override fun createBrowser(p0: Queue?, p1: String?): QueueBrowser = TODO("fake")
    override fun createTemporaryQueue(): TemporaryQueue = TODO("fake")
    override fun createTemporaryTopic(): TemporaryTopic = TODO("fake")
    override fun unsubscribe(p0: String?) = TODO("fake")
    override fun acknowledge() = TODO("fake")

    inner class JMSProducerFake : JMSProducer {
        override fun send(p0: Destination, p1: Message): JMSProducer {
            if (replyTo != null) {
                consumer.messageListener.onMessage(onReplyTo(p1 as TextMessage))
            }
            received.add(p1)
            return this
        }

        override fun setJMSReplyTo(p0: Destination): JMSProducer {
            val p0 = p0 as MQQueue
            replyTo = p0.baseQueueName
            return this
        }

        override fun send(p0: Destination?, p1: String?): JMSProducer = TODO("fake")
        override fun send(p0: Destination?, p1: MutableMap<String, Any>?): JMSProducer = TODO("fake")
        override fun send(p0: Destination?, p1: ByteArray?): JMSProducer = TODO("fake")
        override fun send(p0: Destination?, p1: Serializable?): JMSProducer = TODO("fake")
        override fun setDisableMessageID(p0: Boolean): JMSProducer = TODO("fake")
        override fun getDisableMessageID(): Boolean = TODO("fake")
        override fun setDisableMessageTimestamp(p0: Boolean): JMSProducer = TODO("fake")
        override fun getDisableMessageTimestamp(): Boolean = TODO("fake")
        override fun setDeliveryMode(p0: Int): JMSProducer = TODO("fake")
        override fun getDeliveryMode(): Int = TODO("fake")
        override fun setPriority(p0: Int): JMSProducer = TODO("fake")
        override fun getPriority(): Int = TODO("fake")
        override fun setTimeToLive(p0: Long): JMSProducer = TODO("fake")
        override fun getTimeToLive(): Long = TODO("fake")
        override fun setDeliveryDelay(p0: Long): JMSProducer = TODO("fake")
        override fun getDeliveryDelay(): Long = TODO("fake")
        override fun setAsync(p0: CompletionListener?): JMSProducer = TODO("fake")
        override fun getAsync(): CompletionListener = TODO("fake")
        override fun setProperty(p0: String?, p1: Boolean): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: Byte): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: Short): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: Int): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: Long): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: Float): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: Double): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: String?): JMSProducer = TODO("fake")
        override fun setProperty(p0: String?, p1: Any?): JMSProducer = TODO("fake")
        override fun clearProperties(): JMSProducer = TODO("fake")
        override fun propertyExists(p0: String?): Boolean = TODO("fake")
        override fun getBooleanProperty(p0: String?): Boolean = TODO("fake")
        override fun getByteProperty(p0: String?): Byte = TODO("fake")
        override fun getShortProperty(p0: String?): Short = TODO("fake")
        override fun getIntProperty(p0: String?): Int = TODO("fake")
        override fun getLongProperty(p0: String?): Long = TODO("fake")
        override fun getFloatProperty(p0: String?): Float = TODO("fake")
        override fun getDoubleProperty(p0: String?): Double = TODO("fake")
        override fun getStringProperty(p0: String?): String = TODO("fake")
        override fun getObjectProperty(p0: String?): Any = TODO("fake")
        override fun getPropertyNames(): MutableSet<String> = TODO("fake")
        override fun setJMSCorrelationIDAsBytes(p0: ByteArray?): JMSProducer = TODO("fake")
        override fun getJMSCorrelationIDAsBytes(): ByteArray = TODO("fake")
        override fun setJMSCorrelationID(p0: String?): JMSProducer = TODO("fake")
        override fun getJMSCorrelationID(): String = TODO("fake")
        override fun setJMSType(p0: String?): JMSProducer = TODO("fake")
        override fun getJMSType(): String = TODO("fake")
        override fun getJMSReplyTo(): Destination = TODO("fake")
    }
}

class TextMessageFake(private val msg: String) : TextMessage {
    var correlationID: String = UUID.randomUUID().toString()

    override fun getJMSCorrelationID(): String = correlationID
    override fun getJMSMessageID(): String = correlationID
    override fun setJMSCorrelationID(correlationID: String) {
        // Oppdrag UR skriver over denne, men det kan være nyttig å sette den pga. OTEL
    }

    override fun getText(): String = msg
    override fun setText(msg: String) = TODO("fake")
    override fun setJMSMessageID(id: String?) = TODO("fake")
    override fun getJMSTimestamp(): Long = TODO("fake")
    override fun setJMSTimestamp(timestamp: Long) = TODO("fake")
    override fun getJMSCorrelationIDAsBytes(): ByteArray = TODO("fake")
    override fun setJMSCorrelationIDAsBytes(correlationID: ByteArray?) = TODO("fake")
    override fun getJMSReplyTo(): Destination = TODO("fake")
    override fun setJMSReplyTo(replyTo: Destination?) = TODO("fake")
    override fun getJMSDestination(): Destination = TODO("fake")
    override fun setJMSDestination(destination: Destination?) = TODO("fake")
    override fun getJMSDeliveryMode(): Int = TODO("fake")
    override fun setJMSDeliveryMode(deliveryMode: Int) = TODO("fake")
    override fun getJMSRedelivered(): Boolean = TODO("fake")
    override fun setJMSRedelivered(redelivered: Boolean) = TODO("fake")
    override fun getJMSType(): String = TODO("fake")
    override fun setJMSType(type: String?) = TODO("fake")
    override fun getJMSExpiration(): Long = TODO("fake")
    override fun setJMSExpiration(expiration: Long) = TODO("fake")
    override fun getJMSDeliveryTime(): Long = TODO("fake")
    override fun setJMSDeliveryTime(deliveryTime: Long) = TODO("fake")
    override fun getJMSPriority(): Int = TODO("fake")
    override fun setJMSPriority(priority: Int) = TODO("fake")
    override fun clearProperties() = TODO("fake")
    override fun propertyExists(name: String?): Boolean = TODO("fake")
    override fun getBooleanProperty(name: String?): Boolean = TODO("fake")
    override fun getByteProperty(name: String?): Byte = TODO("fake")
    override fun getShortProperty(name: String?): Short = TODO("fake")
    override fun getIntProperty(name: String?): Int = TODO("fake")
    override fun getLongProperty(name: String?): Long = TODO("fake")
    override fun getFloatProperty(name: String?): Float = TODO("fake")
    override fun getDoubleProperty(name: String?): Double = TODO("fake")
    override fun getStringProperty(name: String?): String = "fake"
    override fun getObjectProperty(name: String?): Any = TODO("fake")
    override fun getPropertyNames(): Enumeration<*> = TODO("fake")
    override fun setBooleanProperty(name: String?, value: Boolean) = TODO("fake")
    override fun setByteProperty(name: String?, value: Byte) = TODO("fake")
    override fun setShortProperty(name: String?, value: Short) = TODO("fake")
    override fun setIntProperty(name: String?, value: Int) = TODO("fake")
    override fun setLongProperty(name: String?, value: Long) = TODO("fake")
    override fun setFloatProperty(name: String?, value: Float) = TODO("fake")
    override fun setDoubleProperty(name: String?, value: Double) = TODO("fake")
    override fun setStringProperty(name: String?, value: String?) = TODO("fake")
    override fun setObjectProperty(name: String?, value: Any?) = TODO("fake")
    override fun acknowledge() = TODO("fake")
    override fun clearBody() = TODO("fake")
    override fun <T : Any?> getBody(c: Class<T>?): T = TODO("fake")
    override fun isBodyAssignableTo(c: Class<*>?): Boolean = TODO("fake")

}

class JMSConsumerFake : JMSConsumer {
    override fun getMessageListener(): MessageListener {
        return listener
    }

    private lateinit var listener: MessageListener
    override fun setMessageListener(p0: MessageListener) {
        listener = p0
    }

    override fun close() = TODO("fake")
    override fun getMessageSelector(): String = TODO("fake")
    override fun receive(): Message = TODO("fake")
    override fun receive(p0: Long): Message = TODO("fake")
    override fun receiveNoWait(): Message = TODO("fake")
    override fun <T : Any?> receiveBody(p0: Class<T>?): T = TODO("fake")
    override fun <T : Any?> receiveBody(p0: Class<T>?, p1: Long): T = TODO("fake")
    override fun <T : Any?> receiveBodyNoWait(p0: Class<T>?): T = TODO("fake")

}
