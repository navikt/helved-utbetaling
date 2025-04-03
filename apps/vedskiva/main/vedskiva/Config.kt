package vedskiva

import libs.kafka.StreamsConfig

data class Config(
    val kafka: StreamsConfig = StreamsConfig(),
)
