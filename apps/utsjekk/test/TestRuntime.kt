import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import fakes.AzureFake
import fakes.OppdragFake
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.jdbc.PostgresContainer
import libs.utils.appLog
import utsjekk.Config
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
    private val azure = AzureFake()
    private val oppdrag = OppdragFake()

    val context: CoroutineContext = postgres.context

    val config by lazy {
        Config(
            oppdrag = oppdrag.config,
            azure = azure.config,
            postgres = postgres.config,
        )
    }

    fun clear(vararg tables: String) {
        postgres.transaction { con ->
//            con.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
            tables.forEach { con.prepareStatement("TRUNCATE TABLE $it CASCADE").execute() }
//            con.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
        }.also {
            tables.forEach { appLog.info("table '$it' trunctated.") }
        }

    }

    private val ktor = testApplication.apply { start() }

    override fun close() {
        postgres.close()
        ktor.stop()
    }
}

fun NettyApplicationEngine.port(): Int = runBlocking {
    resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
}

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            utsjekk(TestRuntime.config, TestRuntime.context)
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