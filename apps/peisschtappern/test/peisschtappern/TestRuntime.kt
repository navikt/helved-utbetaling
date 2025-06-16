package peisschtappern

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import libs.kafka.*
import libs.postgres.Jdbc
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.utils.logger
import java.io.File
import javax.sql.DataSource

private val testLog = logger("test")

object TestRuntime {
    private val postgres = PostgresContainer("peisschtappern")
    val ktor: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    val azure: AzureFake
    val kafka: StreamsMock
    val vanillaKafka: KafkaFactoryFake
    val jdbc: DataSource
    val context: CoroutineDatasource

    init {
        kafka = StreamsMock()
        azure = AzureFake()
        vanillaKafka= KafkaFactoryFake()
        jdbc = Jdbc.initialize(postgres.config)
        context = CoroutineDatasource(jdbc)
        ktor = embeddedServer(Netty, port = 0) {
            val config: Config = Config(
                azure = azure.config,
                jdbc = postgres.config.copy(migrations = listOf(File("test/premigrations"), File("migrations"))),
                kafka = StreamsConfig("", "", SslConfig("", "", "")),
            )
            peisschtappern(config, kafka, vanillaKafka)
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down TestRunner")
            jdbc.truncate()
            postgres.close()
            ktor.stop(1000L, 5000L)
            azure.close()
        })
        ktor.start(wait = false)
    }

    fun reset() {
        vanillaKafka.reset()
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
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

fun DataSource.truncate() = runBlocking {
    withContext(Jdbc.context) {
        transaction {
            Table.values().forEach {
                coroutineContext.connection.prepareStatement("TRUNCATE TABLE ${it.name} CASCADE").execute()
                testLog.info("table '$it' truncated.")
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class KafkaFactoryFake : KafkaFactory {
    private val producers = mutableMapOf<String, KafkaProducer<*, *>>()
    private val consumers = mutableMapOf<String, KafkaConsumer<*, *>>()

    internal fun reset() {
        producers.clear()
        consumers.clear()
    }

    override fun <K : Any, V> createProducer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
    ): KafkaProducerFake<K, V> {
        return producers.getOrPut(topic.name) { KafkaProducerFake(topic) } as KafkaProducerFake<K, V>
    }

    override fun <K : Any, V> createConsumer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
        resetPolicy: OffsetResetPolicy,
        maxProcessingTimeMs: Int,
        groupId: Int,
    ): KafkaConsumerFake<K, V> {
        return consumers.getOrPut(topic.name) { KafkaConsumerFake(topic) } as KafkaConsumerFake<K, V>
    }
}
