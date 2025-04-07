package vedskiva

import java.io.File
import libs.kafka.StreamsConfig
import libs.postgres.JdbcConfig
import libs.utils.env

data class Config(
    val kafka: StreamsConfig = StreamsConfig(),
    val jdbc: JdbcConfig = JdbcConfig(
        url = env("DB_JDBC_URL"),
        migrations = listOf(File("migrations")),
    )
)
