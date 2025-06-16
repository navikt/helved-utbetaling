package urskog

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import libs.mq.MQ
import libs.utils.logger

class TestTopics(private val kafka: StreamsMock) {
    val avstemming = kafka.testTopic(Topics.avstemming) 
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val kvittering = kafka.testTopic(Topics.kvittering) 
    val status = kafka.testTopic(Topics.status) 
    val simuleringer = kafka.testTopic(Topics.simuleringer) 
    val dryrunAap = kafka.testTopic(Topics.dryrunAap) 
    val dryrunTilleggsstønader = kafka.testTopic(Topics.dryrunTilleggsstønader) 
    val dryrunTiltakspenger = kafka.testTopic(Topics.dryrunTiltakspenger) 
}

val testLog = logger("test")

object TestRuntime {
    val ktor: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    val kafka: StreamsMock
    val mq: MQ
    val ws: WSFake
    val fakes: HttpFakes
    val topics: TestTopics

    init {
        kafka = StreamsMock()
        mq = oppdragURFake()
        ws = WSFake()
        fakes = HttpFakes()
        ktor = embeddedServer(Netty, port = 0) {
            val config: Config = TestConfig.create(fakes.proxyConfig, fakes.azureConfig, fakes.simuleringConfig)
            urskog(config, kafka, mq)
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down TestRunner")
            ktor.stop(1000L, 5000L)
        })
        ktor.start(wait = false)
        topics = TestTopics(kafka)
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

