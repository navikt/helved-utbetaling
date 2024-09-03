import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import fakes.AzureFake
import fakes.KafkaFake
import fakes.OppdragFake
import fakes.UnleashFake
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libs.jdbc.PostgresContainer
import libs.utils.appLog
import utsjekk.Config
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.IverksettingResultatDao
import utsjekk.task.TaskDao
import utsjekk.task.TaskHistoryDao
import utsjekk.utsjekk
import kotlin.coroutines.CoroutineContext

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
    val unleash = UnleashFake()

    val context: CoroutineContext = postgres.context
    val kafka: KafkaFake = KafkaFake()

    val config by lazy {
        Config(
            oppdrag = oppdrag.config,
            azure = azure.config,
            postgres = postgres.config,
            unleash = UnleashFake.config,
            kafka = kafka.config,
        )
    }

    fun clear(vararg tables: String) {
        postgres.transaction { con ->
//            con.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
            tables.forEach { con.prepareStatement("TRUNCATE TABLE $it CASCADE").execute() }
//            con.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
        }.also {
            tables.forEach { appLog.info("table '$it' truncated.") }
        }

    }

    private val ktor = testApplication.apply { start() }

    override fun close() {
        clear(
            TaskDao.TABLE_NAME,
            TaskHistoryDao.TABLE_NAME,
            IverksettingDao.TABLE_NAME,
            IverksettingResultatDao.TABLE_NAME,
        )
        postgres.close()
        ktor.stop()
        oppdrag.close()
        azure.close()
        kafka.close()
    }
}

fun NettyApplicationEngine.port(): Int = runBlocking {
    resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
}

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            utsjekk(
                config = TestRuntime.config,
                context = TestRuntime.context,
                featureToggles = TestRuntime.unleash,
                statusProducer = TestRuntime.kafka
            )
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

suspend fun <T> repeatUntil(
    function: suspend () -> T,
    predicate: (T) -> Boolean,
): T = withTimeout(4000) {
    var result = function()
    while (!predicate(result)) {
        delay(10)
        result = function()
    }
    result
}
