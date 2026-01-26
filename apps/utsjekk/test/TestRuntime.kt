import fakes.AzureFake
import fakes.SimuleringFake
import io.ktor.client.*
import java.util.Properties
import libs.jdbc.*
import libs.kafka.StreamsMock
import libs.ktor.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.CoroutineDatasource
import utsjekk.Config
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

val httpClient = TestRuntime.ktor.httpClient

class TestTopics(kafka: StreamsMock) {
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val status = kafka.testTopic(Topics.status)
    val dryrunDp = kafka.testTopic(Topics.dryrunDp)
    val dryrunTs = kafka.testTopic(Topics.dryrunTs)
    val utbetaling = kafka.testTopic(Topics.utbetaling)
    val saker = kafka.testTopic(Topics.saker)
}

object TestRuntime {
    private val postgres = PostgresContainer("utsjekk")
    val kafka: StreamsMock = StreamsMock()
    val azure : AzureFake = AzureFake()
    val simulering : SimuleringFake = SimuleringFake()
    val jdbc : DataSource = Jdbc.initialize(postgres.config)
    val context : CoroutineDatasource = CoroutineDatasource(jdbc)
    val config: Config = Config(
        simulering = simulering.config,
        azure = azure.config,
        jdbc = postgres.config,
        kafka = StreamsConfig(
            applicationId = "",
            brokers = "",
            ssl = SslConfig("", "", ""),
            additionalProperties = Properties().apply {
                this["state.dir"] = "build/kafka-streams/state"
            }
        ),
    )
    val ktor = KtorRuntime<Config>(
        appName = "utsjekk",
        module = {
            utsjekk(
                config, 
                kafka,
                topology = kafka.append(createTopology()) {
                    consume(Tables.saker)
                }
            )
        },
        onClose = {
            jdbc.truncate(
                "utsjekk",
                IverksettingDao.TABLE_NAME,
                IverksettingResultatDao.TABLE_NAME,
                UtbetalingDao.TABLE_NAME,
            )
            postgres.close()
            simulering.close()
            azure.close()
        },
    )
    val topics = TestTopics(kafka)
}

val http: HttpClient by lazy {
    HttpClient()
}

