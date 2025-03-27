package libs.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import java.util.*

interface ProducerFactory {
    fun <K: Any, V : Any> createProducer(
        streamsConfig: StreamsConfig,
        topic: Topic<K, V>,
    ): Producer<K, V> {
        val producerConfig = ProducerFactoryConfig(
            streamsConfig = streamsConfig,
            clientId = "${streamsConfig.applicationId}-producer-${topic.name}",
        )
        return KafkaProducer(
            producerConfig.toProperties(),
            topic.serdes.key.serializer(),
            topic.serdes.value.serializer(),
        )
    }
}

interface ConsumerFactory {
    fun <K: Any, V : Any> createConsumer(
        streamsConfig: StreamsConfig,
        topic: Topic<K, V>,
        maxEstimatedProcessingTimeMs: Long, // e.g. 4_000
        groupIdSuffix: Int = 1, // used to "reset" the consumer by registering a new
        offsetResetPolicy: OffsetResetPolicy = OffsetResetPolicy.earliest
    ): Consumer<K, V> {
        val consumerConfig = ConsumerFactoryConfig(
            streamsConfig = streamsConfig,
            clientId = "${streamsConfig.applicationId}-consumer-${topic.name}",
            groupId = "${streamsConfig.applicationId}-${topic.name}-$groupIdSuffix",
            maxEstimatedProcessingTimeMs,
            offsetResetPolicy
        )

        return KafkaConsumer(
            consumerConfig.toProperties(),
            topic.serdes.key.deserializer(),
            topic.serdes.value.deserializer()
        )
    }
}

enum class OffsetResetPolicy {
    earliest,
    latest
}

private const val TWO_MIN_MS: Long = 120_000

private class ConsumerFactoryConfig(
    private val streamsConfig: StreamsConfig,
    private val clientId: String,
    private val groupId: String,
    private val maxEstimatedProcessingTimeMs: Long,
    private val autoOffset: OffsetResetPolicy,
) {

    fun toProperties(): Properties = Properties().apply {
        this[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = streamsConfig.brokers
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = autoOffset.name
        this[CommonClientConfigs.CLIENT_ID_CONFIG] = clientId
        this[ConsumerConfig.GROUP_ID_CONFIG] = groupId

        /**
         * Set to 2min + estimated max processing time
         * If max estimated processing time is 4 sec, set it to 124_000
         */
        this[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = TWO_MIN_MS + maxEstimatedProcessingTimeMs

        streamsConfig.ssl?.let { ssl ->
            putAll(ssl.properties())
        }
    }
}

private class ProducerFactoryConfig(
    private val streamsConfig: StreamsConfig,
    private val clientId: String,
) {
    fun toProperties(): Properties = Properties().apply {
        this[CommonClientConfigs.CLIENT_ID_CONFIG] = clientId
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = streamsConfig.brokers
        this[ProducerConfig.ACKS_CONFIG] = "all"
        this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "5"
        this[ProducerConfig.COMPRESSION_TYPE_CONFIG] = streamsConfig.compressionType
        streamsConfig.ssl?.let { putAll(it.properties()) }
    }
}
