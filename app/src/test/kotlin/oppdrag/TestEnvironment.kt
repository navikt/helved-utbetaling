package oppdrag

import oppdrag.postgres.Postgres
import oppdrag.postgres.map
import oppdrag.postgres.transaction
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import javax.sql.DataSource

// todo: @AfterAll: close resources
object TestEnvironment {
    val config: Config
    val datasource: DataSource
    val mqFake: MQFake

    init {
        val postgres = PostgreSQLContainer<Nothing>("postgres:16").apply { start() }
        val mq = GenericContainer<Nothing>("ibmcom/mq").apply {
            withEnv("LICENSE", "accept")
            withEnv("MQ_QMGR_NAME", "QM1")
            withExposedPorts(1414)
            start()
        }
        config = config(postgres, mq)
        datasource = init(config.postgres)
        mqFake = MQFake(config.oppdrag).apply { start() }
    }

    fun <T> transaction(block: (Connection) -> T) : T {
        return datasource.transaction(block)
    }

    fun clearTables() {
        transaction { con ->
            con.prepareStatement("TRUNCATE TABLE oppdrag_lager").execute()
            con.prepareStatement("TRUNCATE TABLE simulering_lager").execute()
            con.prepareStatement("TRUNCATE TABLE mellomlagring_konsistensavstemming").execute()
        }
    }

    fun tableSize(table: String): Int? =
        transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM $table")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }
}

fun resources(filename: String): String =
    {}::class.java.getResource(filename)!!.openStream().bufferedReader().readText()

private fun init(config: PostgresConfig) =
    Postgres.createAndMigrate(config) {
        initializationFailTimeout = 30_000
        idleTimeout = 10_000
        connectionTimeout = 10_000
        maxLifetime = 900_000
        connectionTestQuery = "SELECT 1"
    }

private fun config(
    postgres: PostgreSQLContainer<Nothing>,
    mq: GenericContainer<Nothing>,
): Config =
    Config(
        avstemming = AvstemmingConfig(
            enabled = true,
        ),
        oppdrag = OppdragConfig(
            enabled = true,
            mq = MQConfig(
                host = "localhost",
                port = mq.firstMappedPort,
                channel = "DEV.ADMIN.SVRCONN",
                manager = "QM1", // todo: hent fra det som er konfigurert i testcontaineren
                username = "admin",
                password = "passw0rd",
            ),
            kvitteringsKø = "DEV.QUEUE.2",
            sendKø = "DEV.QUEUE.1"
        ),
        postgres = PostgresConfig(
            host = postgres.host,
            port = postgres.firstMappedPort.toString(),
            database = postgres.databaseName,
            username = postgres.username,
            password = postgres.password
        )
    )
