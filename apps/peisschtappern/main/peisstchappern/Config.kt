package peisen

import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.postgres.JdbcConfig

data class Config(
    val jdbc: JdbcConfig = JdbcConfig(),
    val kafka: StreamsConfig = StreamsConfig(),
    val azure: AzureConfig = AzureConfig(),
)
