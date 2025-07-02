import fakes.AbetalClientFake
import fakes.AzureFake
import fakes.OppdragFake
import fakes.SimuleringFake
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
import libs.kafka.StreamsMock
import libs.ktor.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.connection
import libs.jdbc.concurrency.transaction
import libs.utils.logger
import utsjekk.Config
import utsjekk.Topics
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utsjekk
import javax.sql.DataSource

private val testLog = logger("test")

val httpClient = TestRuntime.ktor.httpClient

class TestTopics(private val kafka: StreamsMock) {
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val status = kafka.testTopic(Topics.status)
}

object TestRuntime {
    private val postgres = PostgresContainer("utsjekk")
    val kafka: StreamsMock = StreamsMock()
    val azure : AzureFake = AzureFake()
    val oppdrag : OppdragFake = OppdragFake()
    val simulering : SimuleringFake = SimuleringFake()
    val abetalClient : AbetalClientFake = AbetalClientFake()
    val jdbc : DataSource = Jdbc.initialize(postgres.config)
    val context : CoroutineDatasource = CoroutineDatasource(jdbc)
    val config: Config = Config(
        oppdrag = oppdrag.config,
        simulering = simulering.config,
        abetal = abetalClient.config,
        azure = azure.config,
        jdbc = postgres.config,
        kafka = kafka.config,
    )
    val ktor = KtorRuntime<Config>(
        appName = "utsjekk",
        module = {
            utsjekk(config, kafka)
        },
        onClose = {
            jdbc.truncate(
                "utsjekk",
                IverksettingDao.TABLE_NAME,
                IverksettingResultatDao.TABLE_NAME,
                UtbetalingDao.TABLE_NAME,
            )
            postgres.close()
            oppdrag.close()
            simulering.close()
            azure.close()
        },
    )
    val topics = TestTopics(kafka)
}

val http: HttpClient by lazy {
    HttpClient()
}

