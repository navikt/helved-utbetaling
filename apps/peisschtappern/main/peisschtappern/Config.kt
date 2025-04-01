package peisschtappern

import java.io.File
import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.postgres.JdbcConfig

data class Config(
    val jdbc: JdbcConfig = JdbcConfig(migrations = listOf(File("migrations"))),
    val kafka: StreamsConfig = StreamsConfig(),
    val azure: AzureConfig = AzureConfig(),
)
