package libs.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer as InternalKafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer as InternalKafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import java.util.*
import libs.utils.*
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import net.logstash.logback.argument.StructuredArguments.kv
import org.apache.kafka.common.utils.Utils

fun partition(key: String, numberOfPartitions: Int = 3): Int {
    val bytes = key.toByteArray()
    val hash = Utils.murmur2(bytes)
    return Utils.toPositive(hash) % numberOfPartitions 
}

const val NUM_OF_PARTITION = 3

data class SendResult(
    val isSuccess: Boolean,
    val offset: Long? = null,
    val partition: Int? = null,
    val topic: String? = null,
)

open class KafkaProducer<K: Any, V>(
    private val topic: Topic<K, V & Any>,
    private val producer: Producer<K, V>,
): AutoCloseable {

    fun send(key: K, value: V, headers: Map<String, String> = emptyMap()): SendResult {
        val record = if (key is String) {
            val partition = partition(key as String, NUM_OF_PARTITION)
            ProducerRecord<K, V>(topic.name, partition, key, value)
        } else {
            // TODO: hvis K ikke er String, så må vi ta inn serde
            kafkaLog.error("key $key was not string, and we cannot calculate partition. Using default", key)
            ProducerRecord<K, V>(topic.name, key, value)
        }
        headers.forEach { (k, v) -> 
            record.headers().add(k, v.toByteArray(Charsets.UTF_8))
        }
        return send(record)
    }

    fun send(key: K, value: V, partition: Int): SendResult {
        return send(ProducerRecord<K, V>(topic.name, partition, key, value))
    }

    fun tombstone(key: K, numberOfPartitions: Int = 3): SendResult {
        if (key is String) {
            val partition = partition(key as String, numberOfPartitions)
            return send(ProducerRecord<K, V>(topic.name, partition, key, null))
        } else {
            kafkaLog.warn("key $key was not string, and we cannot calculate partition. Usingn default", key)
            return send(ProducerRecord<K, V>(topic.name, key, null))
        }
    }

    private fun send(record: ProducerRecord<K, V>): SendResult {
        var res = SendResult(false)
        producer.send(record) { md, err ->
            when (err) {
                null -> {
                    kafkaLog.trace("produce ${topic.name}", kv("key", record.key()), kv("topic", topic.name), kv("partition", md.partition()), kv("offset", md.offset())) 
                    res = SendResult(true, md.offset(), md.partition(), md.topic())
                }
                else -> {
                    kafkaLog.error("Failed to produce record for ${record.key()} on ${topic.name}:${md.partition()}")
                    secureLog.error("Failed to produce record for ${record.key()} on ${topic.name}:${md.partition()}", err)
                }
            }
        }.get()
        return res
    }

    override fun close() = producer.close()
} 

open class KafkaConsumer<K: Any, V>(
    private val topic: Topic<K, V & Any>,
    private val consumer: Consumer<K, V>,
): AutoCloseable {

    fun seekToBeginning(vararg partition: Int) {
        val partitions = partition.toList().map { TopicPartition(topic.name, it) }
        consumer.assign(partitions)
        consumer.seekToBeginning(partitions)
    }

    fun poll(timeout: Duration): List<Record<K, V?>> {
        return consumer.poll(timeout.toJavaDuration()).map { Record(it.key(), it.value(), it.partition()) }
    }

    override fun close() = consumer.close()
}

data class Record<K: Any, V>(
    val key: K,
    val value: V,
    val partition: Int
)

interface KafkaFactory {
    fun <K: Any, V> createProducer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
    ): KafkaProducer<K, V> {
        val config = ProducerFactoryConfig(
            streamsConfig = config,
            clientId = "${config.applicationId}-producer-${topic.name}",
        )
        val internal =  InternalKafkaProducer(
            config.toProperties(),
            topic.serdes.key.serializer(),
            topic.serdes.value.serializer(),
        )
        return KafkaProducer(topic, internal)
    }

    fun <K: Any, V> createConsumer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
        resetPolicy: OffsetResetPolicy = OffsetResetPolicy.earliest, 
        maxProcessingTimeMs: Int = 4_000,
        groupId: Int = 1,
    ): KafkaConsumer<K, V> {
        val config = ConsumerFactoryConfig(
            streamsConfig = config,
            clientId = "${config.applicationId}-consumer-${topic.name}",
            groupId = "${config.applicationId}-${topic.name}-$groupId",
            maxProcessingTimeMs,
            resetPolicy
        )
        val internal = InternalKafkaConsumer(
            config.toProperties(),
            topic.serdes.key.deserializer(),
            topic.serdes.value.deserializer()
        )
        return KafkaConsumer(topic, internal)
    }
}

enum class OffsetResetPolicy {
    earliest,
    latest
}

private const val TWO_MIN_MS: Int = 120_000

private class ConsumerFactoryConfig(
    private val streamsConfig: StreamsConfig,
    private val clientId: String,
    private val groupId: String,
    private val maxEstimatedProcessingTimeMs: Int,
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
