package oppdrag

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import libs.auth.JwkGenerator
import oppdrag.containers.MQTestContainer
import oppdrag.containers.PostgresTestContainer
import oppdrag.fakes.AzureFake
import oppdrag.fakes.OppdragFake
import oppdrag.postgres.map
import java.sql.Connection
import javax.jms.TextMessage
import javax.sql.DataSource

object TestEnvironment : AutoCloseable {
    private val postgres: PostgresTestContainer = PostgresTestContainer()
    private val mq: MQTestContainer = MQTestContainer()
    private val azure: AzureFake = AzureFake()

    val config: Config = testConfig(postgres.config, mq.config, azure.config)
    val oppdrag = OppdragFake(config)
    private val jwksGenerator = JwkGenerator(azure.config.issuer, azure.config.clientId)

    fun <T> transaction(block: (Connection) -> T): T = postgres.transaction(block)
    fun <T> withDatasource(block: (DataSource) -> T): T = postgres.withDatasource(block)
    fun generateToken(): String = jwksGenerator.generate()
    fun createSoapMessage(xml: String): TextMessage = oppdrag.createMessage(xml)

    fun clearTables() = transaction { con ->
        con.prepareStatement("TRUNCATE TABLE oppdrag_lager").execute()
        con.prepareStatement("TRUNCATE TABLE simulering_lager").execute()
        con.prepareStatement("TRUNCATE TABLE mellomlagring_konsistensavstemming").execute()
    }

    fun clearMQ() {
        oppdrag.sendKøListener.reset()
        oppdrag.avstemmingKøListener.reset()
    }

    fun tableSize(table: String): Int? = transaction { con ->
        val stmt = con.prepareStatement("SELECT count(*) FROM $table")
        val resultSet = stmt.executeQuery()
        resultSet.map { row -> row.getInt(1) }.singleOrNull()
    }

    override fun close() {
        postgres.close()
        mq.close()
        azure.close()
        oppdrag.close()
    }
}

object Resource {
    fun read(file: String): String {
        return this::class.java.getResource(file)!!.openStream().bufferedReader().readText()
    }
}

fun NettyApplicationEngine.port(): Int = runBlocking {
    resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
}




