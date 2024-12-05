package abetal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.kafka.StreamsMock

object TestTopics {
    val dagpenger by lazy { TestRuntime.kafka.testTopic(Topics.dagpenger) }
    val utbetalinger by lazy { TestRuntime.kafka.testTopic(Topics.utbetalinger) }
    val status by lazy { TestRuntime.kafka.testTopic(Topics.status) }
    val oppdrag by lazy { TestRuntime.kafka.testTopic(Topics.oppdrag) }
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

val httpClient: HttpClient by lazy {
    testApplication.createClient {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
    }
}
