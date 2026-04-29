import fakes.AzureFake
import fakes.SimuleringFake
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.ktor.client.*
import java.io.File
import java.util.Properties
import libs.jdbc.*
import libs.kafka.StreamsMock
import libs.ktor.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.CoroutineDatasource
import utsjekk.Config
import utsjekk.Metrics
import utsjekk.Topics
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.IverksettingResultatDao
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utsjekk
import javax.sql.DataSource
import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import utsjekk.Tables
import utsjekk.createTopology

val httpClient: HttpClient by lazy { TestRuntime.ktor.httpClient }

class TestTopics(kafka: StreamsMock) {
    val dryrunAap = kafka.testTopic(Topics.dryrunAap)
    val dryrunDp = kafka.testTopic(Topics.dryrunDp)
    val dryrunTp = kafka.testTopic(Topics.dryrunTp)
    val dryrunTs = kafka.testTopic(Topics.dryrunTs)
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val saker = kafka.testTopic(Topics.saker)
    val status = kafka.testTopic(Topics.status)
    val utbetaling = kafka.testTopic(Topics.utbetaling)
}

object TestRuntime {
    private val postgres: PostgresContainer by lazy {
        PostgresContainer(
            appname = "utsjekk",
            migrationDirs = listOf(File("migrations")),
            migrate = ::migrateTemplate,
        )
    }
    // Kafka mock and the ktor app form a chicken/egg pair: the StreamsMock
    // instance must exist before the ktor module runs (the module wires it
    // into the topology), but `kafka.testTopic(...)` only works AFTER the
    // module has called `connect()` which initializes the underlying
    // TopologyTestDriver. We hold the mock privately and force ktor init on
    // any external `kafka` access.
    private val kafkaMock: StreamsMock by lazy { StreamsMock() }
    val kafka: StreamsMock
        get() {
            ktor // ensures topology is connected
            return kafkaMock
        }
    val azure: AzureFake by lazy { AzureFake() }
    val simulering: SimuleringFake by lazy { SimuleringFake() }
    val jdbc: DataSource by lazy { Jdbc.initialize(postgres.config) }
    val context: CoroutineDatasource by lazy { CoroutineDatasource(jdbc) }
    val meterRegistry: PrometheusMeterRegistry by lazy { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
    val metrics: Metrics by lazy { Metrics(meterRegistry) }
    val config: Config by lazy {
        val workerId = System.getProperty("org.gradle.test.worker") ?: "0"
        Config(
            simulering = simulering.config,
            azure = azure.config,
            jdbc = postgres.config,
            kafka = StreamsConfig(
                applicationId = "test-application",
                brokers = "localhost:9092",
                ssl = SslConfig("", "", ""),
                additionalProperties = Properties().apply {
                    this["state.dir"] = "build/kafka-streams/state-w$workerId-${System.nanoTime()}"
                }
            ),
        )
    }
    val ktor: KtorRuntime<Config> by lazy {
        KtorRuntime<Config>(
            appName = "utsjekk",
            module = {
                utsjekk(
                    config,
                    kafkaMock,
                    jdbcCtx = context,
                    topology = kafkaMock.append(createTopology(context, metrics)) {
                        consume(Tables.saker)
                    },
                    meterRegistry = meterRegistry,
                    metrics = metrics,
                    startupValidation = {},
                )
            },
            onClose = {
                jdbc.truncate(
                    "utsjekk",
                    IverksettingDao.table,
                    IverksettingResultatDao.table,
                    UtbetalingDao.table,
                )
                postgres.close()
                simulering.close()
                azure.close()
            },
        )
    }
    val topics: TestTopics by lazy {
        // Topology must be registered (via ktor) before testTopic() works.
        ktor
        TestTopics(kafkaMock)
    }
}

val http: HttpClient by lazy {
    HttpClient()
}
