import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import fakes.AbetalClientFake
import fakes.AzureFake
import fakes.OppdragFake
import fakes.SimuleringFake
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import libs.jdbc.PostgresContainer
import libs.kafka.StreamsMock
import libs.postgres.Jdbc
import javax.sql.DataSource
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.TaskHistoryDao
import libs.utils.appLog
import libs.utils.logger
import utsjekk.Config
import utsjekk.Topics
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utsjekk

private val testLog = logger("test")

class TestTopics(private val kafka: StreamsMock) {
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val status = kafka.testTopic(Topics.status)
}

object TestRuntime {
    val ktor: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    val kafka: StreamsMock
    val topics: TestTopics
    private val postgres = PostgresContainer("utsjekk")
    val azure : AzureFake
    val oppdrag : OppdragFake
    val simulering : SimuleringFake
    val abetalClient : AbetalClientFake
    val jdbc : DataSource
    val context : CoroutineDatasource
    val config: Config

    init {
        kafka = StreamsMock()
        azure = AzureFake()
        oppdrag = OppdragFake()
        simulering = SimuleringFake()
        abetalClient = AbetalClientFake()
        jdbc = Jdbc.initialize(postgres.config)
        context = CoroutineDatasource(jdbc)
        config = Config(
            oppdrag = oppdrag.config,
            simulering = simulering.config,
            abetal = abetalClient.config,
            azure = azure.config,
            jdbc = postgres.config,
            kafka = kafka.config,
        )
        ktor = embeddedServer(Netty, port = 0) {
            utsjekk(config, kafka)
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down TestRunner")
            jdbc.truncate(
                TaskDao.TABLE_NAME,
                TaskHistoryDao.TABLE_NAME,
                IverksettingDao.TABLE_NAME,
                IverksettingResultatDao.TABLE_NAME,
                UtbetalingDao.TABLE_NAME,
            )
            postgres.close()
            ktor.stop(1000L, 5000L)
            oppdrag.close()
            simulering.close()
            azure.close()
        })
        ktor.start(wait = false)
        topics = TestTopics(kafka)
    }
}

fun DataSource.truncate(vararg tables: String) = runBlocking {
    withContext(Jdbc.context) {
        transaction {
            tables.forEach {
                coroutineContext.connection.prepareStatement("TRUNCATE TABLE $it CASCADE").execute()
                testLog.info("table '$it' truncated.")
            }
        }
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

val http: HttpClient by lazy {
    HttpClient()
}

val httpClient: HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    defaultRequest {
        url("http://localhost:${TestRuntime.ktor.engine.port}")
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

