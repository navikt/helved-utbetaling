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
import kotlinx.coroutines.withContext
import libs.jdbc.PostgresContainer
import libs.mq.MQContainer
import libs.postgres.Jdbc
import libs.postgres.Migrator
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.utils.logger
import oppdrag.fakes.AzureFake
import oppdrag.fakes.OppdragFake
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import java.io.File

val testLog = logger("test")

object TestRuntime : AutoCloseable {
    val azure: AzureFake = AzureFake()
    val postgres: PostgresContainer = PostgresContainer("oppdrag")
    val mq: MQContainer = MQContainer("oppdrag")
    val config: Config = TestConfig.create(postgres.config, mq.config, azure.config)
    val oppdrag = OppdragFake(config)
    val datasource = Jdbc.initialize(config.postgres)
    val context = CoroutineDatasource(datasource)
    val ktor = testApplication.apply { start() }

    fun clear() {
        val tables = listOf(OppdragLagerRepository.TABLE_NAME)

        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    tables.forEach { table ->
                        coroutineContext.connection.prepareStatement("TRUNCATE TABLE $table").execute()
                    }
                }
            }
        }
        oppdrag.sendKø.clearReceived()
        oppdrag.avstemmingKø.clearReceived()
    }

    override fun close() {
        azure.close()
        oppdrag.close()
        postgres.close()
        mq.close()
        ktor.stop()
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down test environment")
            close()
        })
    }
}

fun NettyApplicationEngine.port(): Int = runBlocking {
    resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
}

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
//            database(TestRuntime.config.postgres)

            runBlocking {
                withContext(TestRuntime.context) {
                    Migrator(File("migrations")).migrate()
                }
            }
            server(TestRuntime.config)
        }
    }
}

val httpClient: HttpClient by lazy {
    testApplication.createClient {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
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
