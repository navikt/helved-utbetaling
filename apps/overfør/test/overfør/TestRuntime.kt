package overfør

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.kafka.Streams
import libs.kafka.StreamsMock
import libs.mq.MQContainer

object TestRuntime : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down TestRunner")
            close()
        })
    }

    val kafka: Streams = StreamsMock()
    val mq: MQContainer = MQContainer("overfør")

    val config: Config = TestConfig.create(mq.config)

    val ktor = testApplication.apply { runBlocking { start() }}

    override fun close() {
        ktor.stop()
        mq.close()
    }
}

fun NettyApplicationEngine.port(): Int = runBlocking {
    resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
}

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            overfør(TestRuntime.config, TestRuntime.kafka)
        }
    }
}

