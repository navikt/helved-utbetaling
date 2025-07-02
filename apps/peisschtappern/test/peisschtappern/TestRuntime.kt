package peisschtappern

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
import libs.jdbc.*
import libs.kafka.*
import libs.ktor.KtorRuntime
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.connection
import libs.jdbc.concurrency.transaction
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
    )

    val ktor = KtorRuntime<Config>(
        appName = "peisschtappern",
        module = { 
            peisschtappern(config, kafka, vanillaKafka)
        },
        onClose = {
            jdbc.truncate("peisschtappern", *Table.values().map{it.name}.toTypedArray())
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
