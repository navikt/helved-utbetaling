package abetal

import libs.kafka.StreamsConfig
import java.util.*

data class Config(
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            // Vi har 3 partisjoner, for å ha en standby-replica må vi ha 4 poder.
            // For å bruke 1 pod, kan vi ikke lenger ha noen standby-replicas
            this[org.apache.kafka.streams.StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG] = 0

            // Vi har 3 partisjoner, hver trenger en tråd på en egen CPU. 
            // Derfor trenger i 3000m CPU og -XX:ActiveProcessorCount=3
            this[org.apache.kafka.streams.StreamsConfig.NUM_STREAM_THREADS_CONFIG] = 3

            // this[org.apache.kafka.streams.StreamsConfig.PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG] = ProduceNextHandler::class.java
            // this[org.apache.kafka.streams.StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG ] = ProcessNextHandler::class.java
            //this[org.apache.kafka.streams.StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = ConsumeNextHandler::class.java

            // AdminClient trenger lengre tid ved opprettelse av internal topics
            this[org.apache.kafka.streams.StreamsConfig.RETRY_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MS_CONFIG] = 1000
        }
    ),
)

