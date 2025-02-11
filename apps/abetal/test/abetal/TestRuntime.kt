package abetal

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import io.ktor.server.testing.*
import libs.kafka.StreamsConfig
import libs.kafka.SslConfig

object TestTopics {
    val aap by lazy { TestRuntime.kafka.testTopic(Topics.aap) }
    val utbetalinger by lazy { TestRuntime.kafka.testTopic(Topics.utbetalinger) }
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

    val config by lazy {
        Config(
            kafka = StreamsConfig("", "", SslConfig("", "", ""))
        )
    }

    private val ktor = testApplication.apply { runBlocking { start() } }

    override fun close() {
        ktor.stop()
        kafka.close()
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            abetal(TestRuntime.config, TestRuntime.kafka)
        }
    }
}

