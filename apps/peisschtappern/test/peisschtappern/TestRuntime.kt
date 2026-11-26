package peisschtappern

import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
import libs.jdbc.concurrency.CoroutineDatasource
import libs.kafka.KafkaConsumer
import libs.kafka.KafkaConsumerFake
import libs.kafka.KafkaFactory
import libs.kafka.KafkaProducer
import libs.kafka.KafkaProducerFake
import libs.kafka.OffsetResetPolicy
import libs.kafka.StreamsConfig
import libs.kafka.StreamsMock
import libs.kafka.Topic
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.io.File
import javax.sql.DataSource

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
    val auditLogs: AuditLogReaderFake by lazy { AuditLogReaderFake() }
    const val auditLogResource = "projects/test/locations/europe-north1/buckets/TeamAudit/views/_AllLogs"
    const val databaseAuditLogResource = "projects/test"
    // Connect the topology before exposing Kafka test topics.
    private val kafkaMock: StreamsMock by lazy { StreamsMock() }
    val kafka: StreamsMock
        get() {
            app // ensures topology is connected
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
            auditLogBucket = auditLogResource,
            databaseAuditLog = databaseAuditLogResource,
        )
    }

    val app: Peisschtappern by lazy { peisschtappern(config, kafkaMock, auditLogs) }

    private val server by lazy {
        app.handler.asServer(SunHttpLoom(0)).start().also { server ->
            Runtime.getRuntime().addShutdownHook(Thread {
                server.stop()
                app.close()
                azure.close()
                postgres.close()
            })
        }
    }

    val httpClient: HttpHandler by lazy {
        ClientFilters.SetBaseUriFrom(Uri.of("http://localhost:${server.port()}")).then(JavaHttpClient())
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
