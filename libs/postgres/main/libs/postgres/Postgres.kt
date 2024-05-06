package libs.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import libs.utils.appLog
import libs.utils.secureLog
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

object Postgres {
    fun initialize(
        config: PostgresConfig,
        hikariConfig: HikariConfig.() -> Unit = {},
    ): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                username = config.username
                password = config.password
                jdbcUrl = config.url
                driverClassName = config.driver
                minimumIdle = 1
                maximumPoolSize = 8
            }.apply(hikariConfig)
        )

    fun DataSource.migrate() {
        Flyway
            .configure()
            .dataSource(this)
            .load()
            .migrate()
    }
}

fun <T : Any> ResultSet.asyncMap(block: (ResultSet) -> T): Sequence<T> =
    sequence {
        while (next()) yield(block(this@asyncMap))
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
            appLog.error("Rolling back database transaction, stacktrace in secureLogs")
            secureLog.error("Rolling back database transaction", it)
            connection.rollback()
            connection.autoCommit = true
        }.getOrThrow()
    }
}
