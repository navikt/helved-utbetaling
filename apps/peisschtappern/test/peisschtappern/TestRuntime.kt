package peisschtappern

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import libs.jdbc.PostgresContainer
import libs.kafka.*
import libs.postgres.Jdbc
import libs.utils.logger
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction

private val testLog = logger("test")

object TestRuntime : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down TestRunner")
            close()
        })
    }

    private val postgres = PostgresContainer("peisschtappern")
    val azure = AzureFake()
    val kafka = StreamsMock()
    val vanillaKafka = KafkaFactoryFake()
    val jdbc = Jdbc.initialize(postgres.config)
    val context = CoroutineDatasource(jdbc)

    val config by lazy {
        Config(
            azure = azure.config,
            jdbc = postgres.config.copy(migrations = listOf(File("test/premigrations"), File("migrations"))),
            kafka = StreamsConfig("", "", SslConfig("", "", "")),
        )
    }

    fun truncate() {
        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    Table.values().forEach {
                        coroutineContext.connection.prepareStatement("TRUNCATE TABLE ${it.name} CASCADE").execute()
                        testLog.info("table '$it' truncated.")
                    }
                }
            }
        }
    }

    fun delete() {
        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    Table.values().forEach {
                        coroutineContext.connection.prepareStatement("DROP TABLE ${it.name} CASCADE").execute()
                        testLog.info("table '$it' dropped.")
                    }
                    coroutineContext.connection.prepareStatement("DROP TABLE migrations CASCADE").execute()
                    testLog.info("table 'migration' dropped.")
                }
            }
        }
    }

    private val ktor = testApplication.apply { runBlocking { start() } }

    override fun close() {
        truncate()
        // delete()
        postgres.close()
        ktor.stop()
        azure.close()
    }

    fun reset() {
        vanillaKafka.reset()
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            peisschtappern(TestRuntime.config, TestRuntime.kafka, TestRuntime.vanillaKafka)
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