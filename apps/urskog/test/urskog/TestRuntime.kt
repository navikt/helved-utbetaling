package urskog

import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.kafka.StreamsMock
import libs.ktor.KtorRuntime
import libs.mq.MQ
import libs.utils.logger
import java.io.File
import javax.sql.DataSource

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

val testLog = logger("test")

object TestRuntime {
    private val postgres = PostgresContainer("urskog")
    val jdbc: DataSource = Jdbc.initialize(postgres.config)
    val context: CoroutineDatasource = CoroutineDatasource(jdbc)
    val kafka: StreamsMock = StreamsMock()
    val mq: MQFake = MQFake()
    val ws: WSFake = WSFake()
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
            jdbc.truncate("urskog", DaoOppdrag.TABLE, DaoPendingUtbetaling.TABLE)
            postgres.close()
        }
    )
    val topics: TestTopics = TestTopics(kafka)
}

