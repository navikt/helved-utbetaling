package oppdrag.containers

import oppdrag.PostgresConfig
import oppdrag.isGHA
import oppdrag.postgres.Postgres
import oppdrag.postgres.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import javax.sql.DataSource

class PostgresContainer : AutoCloseable {
    private val container = PostgreSQLContainer("postgres:16").apply {
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

    private val datasource by lazy {
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
    }
}
