package peisschtappern

import java.io.File
import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.jdbc.JdbcConfig
import libs.utils.env

data class Config(
    val jdbc: JdbcConfig = JdbcConfig(
        url = env("DB_JDBC_URL"), // databaser provisjonert etter juni 2024 må bruke denne
        migrations = listOf(File("migrations")),
    ),
    val kafka: StreamsConfig = StreamsConfig(),
    val azure: AzureConfig = AzureConfig(),
    val image: String = env("NAIS_APP_IMAGE"),
    val auditLogBucket: String? = System.getenv("AUDIT_LOG_BUCKET"),
    val databaseAuditLog: String? = System.getenv("DATABASE_AUDIT_LOG"),
)
