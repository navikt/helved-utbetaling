package oppdrag.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import oppdrag.PostgresConfig
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
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
        try {
            connection.autoCommit = false
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
}

fun ResultSet.getUUID(columnLabel: String): UUID = UUID.fromString(this.getString(columnLabel))

fun PreparedStatement.setNullableObject(index: Int, obj: Any?, type: Int) {
    if (obj != null) {
        this.setObject(index, obj)
    } else {
        this.setNull(index, type)
    }
}