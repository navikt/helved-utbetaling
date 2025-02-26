package abetal

import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import io.ktor.server.testing.*
import libs.kafka.StreamsConfig
import libs.kafka.SslConfig
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import java.util.*

object TestTopics {
    val aap by lazy { TestRuntime.kafka.testTopic(Topics.aap) }
    val saker by lazy { TestRuntime.kafka.testTopic(Topics.saker) }
    val utbetalinger by lazy { TestRuntime.kafka.testTopic(Topics.utbetalinger) }
    val oppdrag by lazy { TestRuntime.kafka.testTopic(Topics.oppdrag) }
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
            kafka = StreamsConfig("", "", SslConfig("", "", ""), additionalProperties = Properties().apply {
                put("state.dir", "build/kafka-streams")
                put("max.task.idle.ms", -1L)
                put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
            })
        )
    }

    private val ktor = testApplication.apply { runBlocking { start() } }

    override fun close() {
        ktor.stop()
    }
}

private val testApplication: TestApplication by lazy {
    TestApplication {
        application {
            abetal(TestRuntime.config, TestRuntime.kafka)
        }
    }
}

