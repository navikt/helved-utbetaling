package abetal

import libs.kafka.StreamsMock
import libs.ktor.*
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import java.io.File
import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import javax.sql.DataSource
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import java.util.*

class TestTopics(kafka: StreamsMock) {
    val aap = kafka.testTopic(Topics.aap)
    val dp = kafka.testTopic(Topics.dp)
    val ts = kafka.testTopic(Topics.ts) 
    val tp = kafka.testTopic(Topics.tp)
    val historisk = kafka.testTopic(Topics.historisk)
    val saker = kafka.testTopic(Topics.saker) 
    val utbetalinger = kafka.testTopic(Topics.utbetalinger) 
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val status = kafka.testTopic(Topics.status) 
    val simulering = kafka.testTopic(Topics.simulering) 
    val pendingUtbetalinger = kafka.testTopic(Topics.pendingUtbetalinger) 
    val fk = kafka.testTopic(Topics.fk) 
    val dpIntern = kafka.testTopic(Topics.dpIntern) 
    val tsIntern = kafka.testTopic(Topics.tsIntern)
    val historiskIntern = kafka.testTopic(Topics.historisk)
}

object TestRuntime {
    private val postgres = PostgresContainer("abetal")
    val jdbc: DataSource = Jdbc.initialize(postgres.config)
    val context: CoroutineDatasource = CoroutineDatasource(jdbc)
    val kafka: StreamsMock = StreamsMock()
    val config = Config(
        jdbc = postgres.config.copy(migrations = listOf(File("test/premigrations"), File("migrations"))),
        kafka = kafka.config.copy(additionalProperties = Properties().apply {
            put("state.dir", "build/kafka-streams")
            put("max.task.idle.ms", -1L)
            put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
        })
    )
    val ktor = KtorRuntime<Config>(
        appName = "abetal",
        module = {
            abetal(
                config = config, 
                kafka = kafka, 
                topology = createTopology(kafka)
            )
        },
        onClose = {
            jdbc.truncate("abetal", DaoFks.TABLE)
            postgres.close()
        }
    )
    val topics: TestTopics = TestTopics(kafka)
}

