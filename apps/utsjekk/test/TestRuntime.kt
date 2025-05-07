import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import fakes.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import libs.jdbc.PostgresContainer
import libs.kafka.*
import libs.postgres.Jdbc
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.TaskHistoryDao
import libs.utils.*
import utsjekk.*
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.utbetaling.UtbetalingDao

object TestTopics {
    val oppdrag by lazy { TestRuntime.kafka.testTopic(Topics.oppdrag) }
    val status by lazy { TestRuntime.kafka.testTopic(Topics.status) }
}

object TestRuntime : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down TestRunner")
            close()
        })
    }

    private val postgres = PostgresContainer("utsjekk")
    val azure = AzureFake()
    val oppdrag = OppdragFake()
    val simulering = SimuleringFake()
    val kafka = StreamsMock()
    val jdbc = Jdbc.initialize(postgres.config)
    val context = CoroutineDatasource(jdbc)

    val config by lazy {
        Config(
            oppdrag = oppdrag.config,
            simulering = simulering.config,
            azure = azure.config,
            jdbc = postgres.config,
            kafka = StreamsConfig("", "", SslConfig("", "", "")),
        )
    }

    fun clear(vararg tables: String) {
        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    tables.forEach {
                        coroutineContext.connection.prepareStatement("TRUNCATE TABLE $it CASCADE").execute()
                    }
                }.also {
                    tables.forEach { appLog.info("table '$it' truncated.") }
                }
            }
        }
    }

    private val ktor = testApplication.apply { runBlocking { start() } }

    override fun close() {
        clear(
            TaskDao.TABLE_NAME,
            TaskHistoryDao.TABLE_NAME,
            IverksettingDao.TABLE_NAME,
            IverksettingResultatDao.TABLE_NAME,
            UtbetalingDao.TABLE_NAME,
        )
        postgres.close()
        ktor.stop()
        oppdrag.close()
        simulering.close()
        azure.close()
        // kafka.close()
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            utsjekk(TestRuntime.config, TestRuntime.kafka)
        }
    }
}

val http: HttpClient by lazy {
    HttpClient()
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

fun <T> awaitDatabase(timeoutMs: Long = 3_000, query: suspend () -> T?): T? =
    runBlocking {
        withTimeoutOrNull(timeoutMs) {
            channelFlow {
                withContext(TestRuntime.context + Dispatchers.IO) {
                    while (true) transaction {
                        query()?.let { send(it) }
                        delay(50)
                    }
                }
            }.firstOrNull()
        }
    }
