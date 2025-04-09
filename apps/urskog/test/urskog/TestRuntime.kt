package urskog

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import libs.mq.MQ
import libs.utils.logger

object TestTopics {
    val avstemming by lazy { TestRuntime.kafka.testTopic(Topics.avstemming) }
    val oppdrag by lazy { TestRuntime.kafka.testTopic(Topics.oppdrag) }
    val kvittering by lazy { TestRuntime.kafka.testTopic(Topics.kvittering) }
    val status by lazy { TestRuntime.kafka.testTopic(Topics.status) }
    val simuleringer by lazy { TestRuntime.kafka.testTopic(Topics.simuleringer) }
    val dryrunAap by lazy { TestRuntime.kafka.testTopic(Topics.dryrunAap) }
    val dryrunTilleggsstønader by lazy { TestRuntime.kafka.testTopic(Topics.dryrunTilleggsstønader) }
    val dryrunTiltakspenger by lazy { TestRuntime.kafka.testTopic(Topics.dryrunTiltakspenger) }
}

val testLog = logger("test")

object TestRuntime : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down TestRunner")
            close()
        })
    }

    val kafka: StreamsMock = StreamsMock()
    val mq: MQ = oppdragURFake()
    val ws: WSFake = WSFake()
    val fakes: HttpFakes = HttpFakes()
    val config: Config = TestConfig.create(fakes.proxyConfig, fakes.azureConfig, fakes.simuleringConfig)
    val ktor = testApplication.apply { runBlocking { start() }}

    override fun close() {
        ktor.stop()
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            urskog(TestRuntime.config, TestRuntime.kafka, TestRuntime.mq)
        }
    }
}

