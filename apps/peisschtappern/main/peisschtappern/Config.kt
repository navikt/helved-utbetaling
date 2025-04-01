package peisschtappern

import java.io.File
import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.postgres.JdbcConfig
import libs.utils.env

data class Config(
    val jdbc: JdbcConfig = JdbcConfig(
        url = env("DB_URL"), // databaser provisjonert etter juni 2024 må bruke denne
        migrations = listOf(File("migrations")),
    ),
    val kafka: StreamsConfig = StreamsConfig(),
    val azure: AzureConfig = AzureConfig(),
)
