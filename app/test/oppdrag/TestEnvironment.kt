package oppdrag

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.utils.appLog
import oppdrag.containers.MQTestContainer
import oppdrag.containers.PostgresTestContainer
import oppdrag.fakes.AzureFake
import oppdrag.fakes.OppdragFake
import oppdrag.postgres.map

object TestEnvironment : AutoCloseable {

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down test environment")
            close()
        })
    }

    val mq: MQTestContainer = MQTestContainer()
    val azure: AzureFake = AzureFake()
    val postgres: PostgresTestContainer = PostgresTestContainer()
    val config: Config = testConfig(postgres.config, mq.config, azure.config)
    val oppdrag = OppdragFake(config)

    fun clearTables() = postgres.transaction { con ->
        con.prepareStatement("TRUNCATE TABLE oppdrag_lager").execute()
        con.prepareStatement("TRUNCATE TABLE simulering_lager").execute()
        con.prepareStatement("TRUNCATE TABLE mellomlagring_konsistensavstemming").execute()
    }

    fun clearMQ() {
        oppdrag.sendKø.clearReceived()
        oppdrag.avstemmingKø.clearReceived()
    }

    fun tableSize(table: String): Int? = postgres.transaction { con ->
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

val ApplicationTestBuilder.httpClient: HttpClient
    get() = createClient {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
    }

/**
 * Replaces the content between the XML tags with the given replacement.
 * @example <tag>original</tag> -> <tag>replacement</tag>
 */
fun String.replaceBetweenXmlTag(tag: String, replacement: String): String {
    return replace(
        regex = Regex("(?<=<$tag>).*(?=</$tag>)"),
        replacement = replacement
    )
}
