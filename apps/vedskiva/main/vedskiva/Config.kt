package vedskiva

import java.io.File
import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.jdbc.JdbcConfig
import libs.utils.env

data class Config(
    val kafka: StreamsConfig = StreamsConfig(),
    val jdbc: JdbcConfig = JdbcConfig(
        url = env("DB_JDBC_URL"),
        migrations = listOf(File("migrations")),
    ),
    val azure: AzureConfig = AzureConfig()
)
