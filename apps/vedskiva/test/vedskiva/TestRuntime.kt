package vedskiva

import libs.jdbc.PostgresContainer
import libs.postgres.Jdbc
import libs.postgres.concurrency.*
import kotlinx.coroutines.*
import libs.utils.logger
import java.io.File
import libs.kafka.*

val testLog = logger("test")

object TestRuntime: AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down TestRunner")
            close()
        })
    }

    private val postgres = PostgresContainer("vedskiva")
    val kafka = KafkaFactoryFake()
    val jdbc = Jdbc.initialize(postgres.config)
    val context = CoroutineDatasource(jdbc)

    val config by lazy {
        Config(
            kafka = StreamsConfig("", "", SslConfig("", "", "")),
            jdbc = postgres.config.copy(migrations = listOf(File("test/migrations"), File("migrations"))),
        )
    }

    override fun close() {
        truncate()
        postgres.close()
    }

    private fun truncate() {
        runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    coroutineContext.connection.prepareStatement("TRUNCATE TABLE ${Scheduled.TABLE_NAME} CASCADE").execute()
                    testLog.info("table '${Scheduled.TABLE_NAME}' truncated.") 
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class KafkaFactoryFake: KafkaFactory {
    private val producers = mutableMapOf<String, KafkaProducer<*, * >>()
    private val consumers = mutableMapOf<String, KafkaConsumer<*, * >>()

    override fun <K: Any, V> createProducer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
    ): KafkaProducerFake<K, V> {
        return producers.getOrPut(topic.name) { KafkaProducerFake(topic) } as KafkaProducerFake<K, V>
    }

    override fun <K: Any, V> createConsumer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
        resetPolicy: OffsetResetPolicy ,  
        maxProcessingTimeMs: Int,
        groupId: Int,
    ): KafkaConsumerFake<K, V> {
        return consumers.getOrPut(topic.name) { KafkaConsumerFake(topic) } as KafkaConsumerFake<K, V>
    }
}
