package libs.jdbc

import libs.postgres.JdbcConfig
import libs.utils.env
import org.testcontainers.containers.PostgreSQLContainer

class PostgresContainer(appname: String) : AutoCloseable {
    private val container = PostgreSQLContainer("postgres:15").apply {
        if (!isGHA()) {
            withLabel("service", appname)
            withReuse(true)
            withNetwork(null)
            withCreateContainerCmdModifier { cmd ->
                cmd.withName("$appname-pg") // FIXME: removing this will remove the flaky 'conflict' container error
                cmd.hostConfig?.apply {
                    withMemory(512 * 1024 * 1024)
                    withMemorySwap(1024 * 1024 * 1024)
                }
            }
        }
        start()
    }

    private fun waitUntilJdbcAvailable(retries: Int = 20, delayMillis: Long = 500) {
        repeat(retries) { attempt ->
            try {
                java.sql.DriverManager.getConnection(
                    container.jdbcUrl,
                    container.username,
                    container.password,
                ).use { con -> 
                    if (!con.isClosed) return 
                }

            } catch (e: Exception) {
                Thread.sleep(delayMillis)
            }
        }
        error("Postgres container did not become available after ${retries * delayMillis} ms")
    }

    val config by lazy {
        waitUntilJdbcAvailable()
        JdbcConfig(
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
    }
}

private fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)
