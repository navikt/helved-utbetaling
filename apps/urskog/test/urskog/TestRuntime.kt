package urskog

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import libs.mq.MQContainer
import libs.mq.MQFake
import libs.mq.MQ

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
    val mq: MQ = oppdragURFake()
    val config: Config = TestConfig.create(8013, 8014)
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

