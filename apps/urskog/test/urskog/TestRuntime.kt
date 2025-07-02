package urskog

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import libs.mq.MQ
import libs.ktor.*
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
    val kafka: StreamsMock = StreamsMock()
    val mq: MQ = oppdragURFake()
    val ws: WSFake = WSFake()
    val fakes: HttpFakes = HttpFakes()
    val config: Config = TestConfig.create(fakes.proxyConfig, fakes.azureConfig, fakes.simuleringConfig)
    val ktor = KtorRuntime<Config>(
        appName = "urskog",
        module = {
            urskog(config, kafka, mq)
        },
    )
    val topics: TestTopics = TestTopics(kafka)
}

