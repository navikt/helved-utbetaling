package urskog

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import libs.mq.MQ

object TestTopics {
    val oppdrag by lazy { TestRuntime.kafka.testTopic(Topics.oppdrag) }
    val kvittering by lazy { TestRuntime.kafka.testTopic(Topics.kvittering) }
    val kvitteringQueue by lazy { TestRuntime.kafka.testTopic(Topics.kvitteringQueue) }
    val status by lazy { TestRuntime.kafka.testTopic(Topics.status) }
    val simulering by lazy { TestRuntime.kafka.testTopic(Topics.simulering) }
    val aapSimulering by lazy { TestRuntime.kafka.testTopic(Topics.aapSimulering) }
}

object TestRuntime : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down TestRunner")
            close()
        })
    }

    val kafka = StreamsMock()
    val mq: MQ = oppdragURFake()
    val ws: WSFake = WSFake()
    val config: Config = TestConfig.create(ws.proxyConfig, ws.azureConfig, ws.simuleringConfig)
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

