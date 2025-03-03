package urskog

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import libs.mq.MQContainer

object TestTopics {
    val oppdrag by lazy { TestRuntime.kafka.testTopic(Topics.oppdrag) }
    // val kvittering by lazy { TestRuntime.kafka.testTopic(Topics.kvittering) }
    // val utbetalinger by lazy { TestRuntime.kafka.testTopic(Topics.utbetalinger) }
    val status by lazy { TestRuntime.kafka.testTopic(Topics.status) }
}

object TestRuntime : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down TestRunner")
            close()
        })
    }

    val kafka = StreamsMock()
    private val mq: MQContainer = MQContainer("urskog")

    val config: Config = TestConfig.create(mq.config, 8013, 8014)

    val oppdrag = URFake(config)

    val ktor = testApplication.apply { runBlocking { start() }}

    override fun close() {
        ktor.stop()
        mq.close()
        oppdrag.close()
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            urskog(TestRuntime.config, TestRuntime.kafka)
        }
    }
}

