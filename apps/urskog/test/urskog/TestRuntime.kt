package urskog

import com.ibm.mq.jms.MQQueue
import libs.auth.AzureConfig
import libs.jdbc.Jdbc
import libs.jdbc.JdbcConfig
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.kafka.StreamsMock
import libs.ktor.KtorRuntime
import libs.mq.MQConfig
import libs.utils.logger
import libs.ws.SoapConfig
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import java.io.File
import java.util.*
import javax.sql.DataSource

val testLog = logger("test")

class TestTopics(kafka: StreamsMock) {
    val avstemming = kafka.testTopic(Topics.avstemming) 
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val pendingUtbetalinger = kafka.testTopic(Topics.pendingUtbetalinger)
    val status = kafka.testTopic(Topics.status)
    val simuleringer = kafka.testTopic(Topics.simuleringer) 
    val dryrunAap = kafka.testTopic(Topics.dryrunAap) 
    val dryrunTilleggsstønader = kafka.testTopic(Topics.dryrunTilleggsstønader) 
    val dryrunTiltakspenger = kafka.testTopic(Topics.dryrunTiltakspenger) 
}

object TestRuntime {
    private val migrationDirs = listOf(File("test/premigrations"), File("migrations"))
    private val postgres: PostgresContainer by lazy {
        PostgresContainer(
            appname = "urskog",
            migrationDirs = migrationDirs,
            migrate = ::migrateTemplate,
        )
    }
    val jdbc: DataSource by lazy { Jdbc.initialize(postgres.config) }
    val context: CoroutineDatasource by lazy { CoroutineDatasource(jdbc) }
    private val kafkaMock: StreamsMock by lazy { StreamsMock() }
    val kafka: StreamsMock
        get() {
            ktor // ensures topology is connected
            return kafkaMock
        }
    val mq: FakeMQ by lazy { FakeMQ() }
    val ws: FakeWS by lazy { FakeWS() }
    val fakes: HttpFakes by lazy { HttpFakes() }
    val config: Config by lazy {
        TestConfig.create(
            proxy = fakes.proxyConfig,
            azure = fakes.azureConfig,
            simulering = fakes.simuleringConfig,
            jdbc = postgres.config,
        )
    }
    val ktor: KtorRuntime<Config> by lazy {
        KtorRuntime<Config>(
            appName = "urskog",
            module = {
                urskog(config, kafkaMock, mq)
            },
            onClose = {
                jdbc.truncate("urskog", DaoOppdrag.table, DaoPendingUtbetaling.table)
                postgres.close()
            }
        )
    }
    val topics: TestTopics by lazy {
        // Topology must be registered (via ktor) before testTopic() works.
        ktor
        TestTopics(kafkaMock)
    }
}

object TestConfig {
    fun create(
        proxy: ProxyConfig,
        azure: AzureConfig,
        simulering: SoapConfig,
        jdbc: JdbcConfig,
    ): Config {
        val oppdrag = OppdragConfig(
            avstemmingKø = MQQueue("DEV.QUEUE.3"),
            kvitteringsKø = MQQueue("DEV.QUEUE.2"),
            sendKø = MQQueue("DEV.QUEUE.1")
        )
        val kafka = StreamsConfig("test-application", "localhost:9092", SslConfig("", "", ""), additionalProperties = Properties().apply {
            put("state.dir", "build/kafka-streams")
            put("max.task.idle.ms", -1L)
            put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
        })
        val mq = MQConfig(
            host = "og hark",
            port = 99,
            channel = "",
            manager = "anders",
            username = "",
            password = "",
        )
        return Config(
            jdbc = jdbc,
            kafka = kafka,
            oppdrag = oppdrag,
            mq = mq,
            proxy = proxy,
            azure = azure,
            simulering = simulering,
        )
    }
}

