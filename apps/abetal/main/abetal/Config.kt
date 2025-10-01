package abetal

import libs.kafka.*
import java.util.Properties

data class Config(
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            // this[org.apache.kafka.streams.StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = ConsumeNextHandler::class.java
        }
    ),
)

