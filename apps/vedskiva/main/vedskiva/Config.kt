package vedskiva

import java.io.File
import java.net.URI
import java.net.URL
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
    val azure: AzureConfig = AzureConfig(),
    val peisschtappern: PeisschtappernConfig = PeisschtappernConfig()
)

data class PeisschtappernConfig(
    val scope: String = env("PEISSCHTAPPERN_SCOPE"),
    val host: URL = env("PEISSCHTAPPERN_HOST", URI("http://peisschtappern").toURL())
)

