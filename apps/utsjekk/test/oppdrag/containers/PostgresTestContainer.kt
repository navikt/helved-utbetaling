package oppdrag.containers

import oppdrag.PostgresConfig
import oppdrag.isGHA
import oppdrag.postgres.Postgres
import oppdrag.postgres.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import javax.sql.DataSource

class PostgresTestContainer : AutoCloseable {
    private val postgres = PostgreSQLContainer("postgres:16").apply {
        if (!isGHA()) {
            withReuse(true)
            withLabel("app", "oppdrag")
            withCreateContainerCmdModifier { it.withName("oppdrag-postgres") }
        }
        withNetwork(null)
        start()
    }

    private val datasource = init(config)

    val config
        get() = PostgresConfig(
            host = postgres.host,
            port = postgres.firstMappedPort.toString(),
            database = postgres.databaseName,
            username = postgres.username,
            password = postgres.password
        )

    fun <T> transaction(block: (Connection) -> T): T {
        return datasource.transaction(block)
    }

    fun <T> withDatasource(block: (DataSource) -> T): T = block(datasource)

    private fun init(config: PostgresConfig): DataSource =
        Postgres.createAndMigrate(config) {
            initializationFailTimeout = 5_000
            idleTimeout = 10_000
            connectionTimeout = 5_000
            maxLifetime = 900_000
        }

    override fun close() {
        if (isGHA()) {
            postgres.close()
        }
    }
}
