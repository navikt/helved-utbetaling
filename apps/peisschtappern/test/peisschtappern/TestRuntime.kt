package peisschtappern

import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
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
    private val migrationDirs = listOf(File("test/premigrations"), File("migrations"))
    private val postgres: PostgresContainer by lazy {
        PostgresContainer(
            appname = "peisschtappern",
            migrationDirs = migrationDirs,
            migrate = ::migrateTemplate,
        )
    }
    val azure: AzureFake by lazy { AzureFake() }
    // Kafka mock and the ktor app form a chicken/egg pair: the StreamsMock
    // instance must exist before the ktor module runs (the module wires it
    // into the topology), but `kafka.testTopic(...)` only works AFTER the
    // module has called `connect()` which initializes the underlying
    // TopologyTestDriver. We construct the mock eagerly inside its first
    // access, then trigger ktor init to register the topology.
    private val kafkaMock: StreamsMock by lazy { StreamsMock() }
    val kafka: StreamsMock
        get() {
            ktor // ensures topology is connected
            return kafkaMock
        }
    val jdbc: DataSource by lazy { Jdbc.initialize(postgres.config) }
    val context: CoroutineDatasource by lazy { CoroutineDatasource(jdbc) }
    val config: Config by lazy {
        Config(
            azure = azure.config,
            jdbc = postgres.config,
            kafka = kafkaMock.config,
            image = "test:test",
        )
    }

    val ktor: KtorRuntime<Config> by lazy {
        KtorRuntime<Config>(
            appName = "peisschtappern",
            module = {
                peisschtappern(config, kafkaMock)
            },
            onClose = {
                jdbc.truncate("peisschtappern", *Table.entries.map{it.name}.toTypedArray(), TimerDao.table)
                postgres.close()
                azure.close()
            }
        )
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
