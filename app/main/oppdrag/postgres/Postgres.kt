package oppdrag.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import libs.utils.appLog
import libs.utils.secureLog
import oppdrag.PostgresConfig
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

internal object Postgres {
    fun createAndMigrate(
        config: PostgresConfig,
        hikariConfig: HikariConfig.() -> Unit = {},
    ): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                username = config.username
                password = config.password
                jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}"
                driverClassName = "org.postgresql.Driver"
                minimumIdle = 1
                maximumPoolSize = 8
            }.apply(hikariConfig)
        ).apply {
            Flyway
                .configure()
                .dataSource(this)
                .validateMigrationNaming(true)
                .load()
                .migrate()
        }
}

fun <T : Any> ResultSet.map(block: (ResultSet) -> T): List<T> =
    sequence {
        while (next()) yield(block(this@map))
    }.toList()

fun <T> DataSource.transaction(block: (Connection) -> T): T {
    return this.connection.use { connection ->
        runCatching {
            connection.autoCommit = false
            block(connection)
        }.onSuccess {
            connection.commit()
            connection.autoCommit = true
        }.onFailure {
            appLog.error("Rolling back database transaction, please check secureLogs")
            secureLog.error("Rolling back database transaction", it)
            connection.rollback()
            connection.autoCommit = true
        }.getOrThrow()
    }
}
