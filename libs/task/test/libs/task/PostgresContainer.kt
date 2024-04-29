package libs.task

import libs.postgres.Postgres
import libs.postgres.PostgresConfig
import libs.postgres.transaction
import libs.utils.appLog
import libs.utils.env
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import javax.sql.DataSource

fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)

class PostgresContainer : AutoCloseable {
    private val container = PostgreSQLContainer("postgres:15").apply {
        if (!isGHA()) {
            withReuse(true)
            withNetwork(null)
            withCreateContainerCmdModifier { cmd ->
                cmd.withName("postgres")
                cmd.hostConfig?.apply {
                    withMemory(512 * 1024 * 1024)
                    withMemorySwap(1024 * 1024 * 1024)
                }
            }
        }
        start()
    }

    fun cleanup() = datasource.transaction { con ->
        con.prepareStatement("TRUNCATE TABLE task").execute()
//        con.prepareStatement("DROP TABLE flyway_schema_history").execute()
    }

    val datasource by lazy {
        Postgres.createAndMigrate(config) {
            initializationFailTimeout = 5_000
            idleTimeout = 10_000
            connectionTimeout = 5_000
            maxLifetime = 900_000
        }
    }

    val config by lazy {
        PostgresConfig(
            host = container.host,
            port = container.firstMappedPort.toString(),
            database = container.databaseName,
            username = container.username,
            password = container.password
        )
    }

    fun <T> transaction(block: (Connection) -> T): T = datasource.transaction(block)
    fun <T> withDatasource(block: (DataSource) -> T): T = block(datasource)

    override fun close() {
        // GitHub Actions service containers or testcontainers is not reusable and must be closed
        if (isGHA()) {
            container.close()
        }

        cleanup()
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down test environment")
            close()
        })
    }
}