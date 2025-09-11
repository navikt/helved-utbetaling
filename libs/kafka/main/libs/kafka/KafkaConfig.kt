package libs.kafka

import libs.utils.env
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.streams.StreamsConfig

data class StreamsConfig(
    val applicationId: String = env("KAFKA_STREAMS_APPLICATION_ID"),
    val brokers: String = env("KAFKA_BROKERS"),
    val ssl: SslConfig? = SslConfig(),
    val compressionType: String = "snappy",
    val additionalProperties: Properties = Properties(),
) {
    fun streamsProperties(): Properties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = applicationId
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = brokers

        ssl?.let { putAll(it.properties()) }

        this[StreamsConfig.PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG] = ProduceAgainHandler::class.java
        this[StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = ConsumeNextHandler::class.java
        this[StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG ] = ProcessAgainHandler::class.java

        // Configuration for resilience
        this[StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG)] = "all"
        this[StreamsConfig.REPLICATION_FACTOR_CONFIG] = 3
        this[StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG] = 1

        // Configuration for decreaseing latency
        this[StreamsConfig.producerPrefix(ProducerConfig.BATCH_SIZE_CONFIG)] = 0 // do not batch
        this[StreamsConfig.producerPrefix(ProducerConfig.LINGER_MS_CONFIG)] = 0 // send immediately

        // Configuration for message size
        this[StreamsConfig.producerPrefix(ProducerConfig.COMPRESSION_TYPE_CONFIG)] = compressionType

        // Default is 'INFO' but <record-e2e-latency-*> is recorded in 'DEBUG'
        this[StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG] = "INFO"

        /*
         * Enable exactly onces semantics:
         * 1. ack produce to sink topic
         * 2. update state in app (state store)
         * 3. commit offset for source topic
         *
         * commit.interval.ms is set to 100ms
         * comsumers are configured with isolation.level="read_committed"
         * processing requires minimum three brokers
         */
        this[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = StreamsConfig.EXACTLY_ONCE_V2

        putAll(additionalProperties)
    }
}

data class SslConfig(
    private val truststorePath: String = env("KAFKA_TRUSTSTORE_PATH"),
    private val keystorePath: String = env("KAFKA_KEYSTORE_PATH"),
    private val credstorePsw: String = env("KAFKA_CREDSTORE_PASSWORD"),
) {
    fun properties() = Properties().apply {
        this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
        this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
        this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststorePath
        this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = credstorePsw
        this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
        this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystorePath
        this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = credstorePsw
        this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = credstorePsw
        this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
    }
}

