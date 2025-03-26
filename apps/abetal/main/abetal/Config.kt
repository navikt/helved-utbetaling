package abetal

import libs.kafka.*
import libs.utils.env
import java.util.Properties

data class Config(
    val kafka: StreamsConfig = StreamsConfig(
        applicationId = "${env<String>("KAFKA_STREAMS_APPLICATION_ID")}v1",
        additionalProperties = Properties().apply {
            put("default.deserialization.exception.handler", ConsumeNextHandler::class.java.name)
        }
    ),
)

