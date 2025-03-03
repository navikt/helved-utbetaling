package abetal

import libs.kafka.*
import java.util.Properties

data class Config(
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            put("default.deserialization.exception.handler", ConsumeNextHandler::class.java.name)
        }
    ),
)

