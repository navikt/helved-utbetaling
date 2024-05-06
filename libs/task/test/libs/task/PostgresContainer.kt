package libs.task

import libs.postgres.Postgres
import libs.postgres.Postgres.migrate
import libs.postgres.PostgresConfig
import libs.postgres.transaction
import libs.utils.appLog
import libs.utils.env
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)

class PostgresContainer : AutoCloseable {
    private val container = PostgreSQLContainer("postgres:15").apply {
        if (!isGHA()) {
            withLabel("service", "task-lib")
            withReuse(true)
            withNetwork(null)
            withCreateContainerCmdModifier { cmd ->
                cmd.withName("postgres2")
                cmd.hostConfig?.apply {
                    withMemory(512 * 1024 * 1024)
                    withMemorySwap(1024 * 1024 * 1024)
                }
            }
        }
        start()
    }

    fun clearTables() = datasource.transaction { con ->
        con.prepareStatement("TRUNCATE TABLE task").execute()
        appLog.info("Cleared table task")
    }

    private fun DataSource.deleteTables() {
        transaction { con ->
            con.prepareStatement("DROP SCHEMA public CASCADE").execute()
            con.prepareStatement("CREATE SCHEMA public").execute()
        }
    }

    val datasource by lazy {
        Postgres.initialize(config) {
            initializationFailTimeout = 5_000
            idleTimeout = 10_000
            connectionTimeout = 5_000
            maxLifetime = 900_000
            maximumPoolSize = 10
        }.apply {
            runCatching {
                migrate()
            }
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

    override fun close() {
        // GitHub Actions service containers or testcontainers is not reusable and must be closed
        if (isGHA()) {
            container.close()
        }

        clearTables()
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down test environment")
            close()
        })
    }
}