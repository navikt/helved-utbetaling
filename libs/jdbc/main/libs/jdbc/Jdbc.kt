package libs.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.sql.ResultSet
import javax.sql.DataSource
import libs.jdbc.concurrency.CoroutineDatasource
import libs.utils.env
import libs.utils.logger

val jdbcLog = logger("jdbc")
/**
 * The Jdbc wrapper for managing the datasource
 */
object Jdbc {
    lateinit var context: CoroutineDatasource

    /**
     * Initialize the datasource once.
     *
     * @param config - the jdbc configuration
     * @param hikariConfig - override default hikari configuration
     * @return the created datasource
     */
    fun initialize(
        config: JdbcConfig,
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
        ).also {
            context = CoroutineDatasource(it)
        }
}

data class JdbcConfig(
    val host: String = env("DB_HOST"),
    val port: String = env("DB_PORT"),
    val database: String = env("DB_DATABASE"),
    val username: String = env("DB_USERNAME"),
    val password: String = env("DB_PASSWORD"),
    val url: String = "jdbc:postgresql://$host:$port/$database",
    val driver: String = "org.postgresql.Driver",
    val migrations: List<File> = listOf(File("main/migrations"))
)

fun <T : Any> ResultSet.map(block: (ResultSet) -> T): List<T> =
    sequence {
        while (next()) yield(block(this@map))
    }.toList()
