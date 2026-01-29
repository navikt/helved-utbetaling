package urskog

import com.ibm.mq.jms.MQQueue
import libs.auth.AzureConfig
import libs.jdbc.Jdbc
import libs.jdbc.JdbcConfig
import libs.jdbc.PostgresContainer
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
    private val postgres = PostgresContainer("urskog")
    val jdbc: DataSource = Jdbc.initialize(postgres.config)
    val context: CoroutineDatasource = CoroutineDatasource(jdbc)
    val kafka: StreamsMock = StreamsMock()
    val mq: FakeMQ = FakeMQ()
    val ws: FakeWS = FakeWS()
    val fakes: HttpFakes = HttpFakes()
    val config: Config = TestConfig.create(
        proxy = fakes.proxyConfig,
        azure = fakes.azureConfig,
        simulering = fakes.simuleringConfig,
        jdbc = postgres.config.copy(migrations = listOf(File("test/premigrations"), File("migrations"))),
    )
    val ktor = KtorRuntime<Config>(
        appName = "urskog",
        module = {
            urskog(config, kafka, mq)
        },
        onClose = {
            jdbc.truncate("urskog", DaoOppdrag.table, DaoPendingUtbetaling.table)
            postgres.close()
        }
    )
    val topics: TestTopics = TestTopics(kafka)
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
        val kafka = StreamsConfig("", "", SslConfig("", "", ""), additionalProperties = Properties().apply {
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

