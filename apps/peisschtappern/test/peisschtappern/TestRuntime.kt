package peisschtappern

import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.kafka.KafkaConsumer
import libs.kafka.KafkaConsumerFake
import libs.kafka.KafkaFactory
import libs.kafka.KafkaProducer
import libs.kafka.KafkaProducerFake
import libs.kafka.OffsetResetPolicy
import libs.kafka.StreamsConfig
import libs.kafka.StreamsMock
import libs.kafka.Topic
import libs.ktor.KtorRuntime
import libs.utils.logger
import java.io.File
import javax.sql.DataSource

private val testLog = logger("test")

object TestRuntime {
    private val postgres = PostgresContainer("peisschtappern")
    val azure: AzureFake = AzureFake()
    val kafka: StreamsMock = StreamsMock()
    val vanillaKafka: KafkaFactoryFake = KafkaFactoryFake()
    val jdbc: DataSource = Jdbc.initialize(postgres.config)
    val context: CoroutineDatasource = CoroutineDatasource(jdbc)
    val config: Config = Config(
        azure = azure.config,
        jdbc = postgres.config.copy(migrations = listOf(File("test/premigrations"), File("migrations"))),
        kafka = kafka.config,
        image = "test:test",
    )

    val ktor = KtorRuntime<Config>(
        appName = "peisschtappern",
        module = { 
            peisschtappern(
                config, 
                kafka, 
                vanillaKafka, 
            )
        },
        onClose = {
            jdbc.truncate("peisschtappern", *Table.entries.map{it.name}.toTypedArray(), TimerDao.TABLE_NAME)
            postgres.close()
            azure.close()
        }
    )

    fun reset() {
        vanillaKafka.reset()
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
