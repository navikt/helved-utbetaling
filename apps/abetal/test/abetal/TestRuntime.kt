package abetal

import io.ktor.server.testing.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*
import kotlinx.coroutines.runBlocking
import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.kafka.StreamsMock
import libs.utils.*
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*

object TestTopics {
    val dp by lazy { TestRuntime.kafka.testTopic(Topics.dp) }
    val saker by lazy { TestRuntime.kafka.testTopic(Topics.saker) }
    val utbetalinger by lazy { TestRuntime.kafka.testTopic(Topics.utbetalinger) }
    val oppdrag by lazy { TestRuntime.kafka.testTopic(Topics.oppdrag) }
    val status by lazy { TestRuntime.kafka.testTopic(Topics.status) }
    val simulering by lazy { TestRuntime.kafka.testTopic(Topics.simulering) }
}

object TestRuntime : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down TestRunner")
            close()
        })
    }

    val kafka = StreamsMock()
    val topology = kafka.append(createTopology()) {
        consume(Tables.saker)
    }

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
            abetal(TestRuntime.config, TestRuntime.kafka, TestRuntime.topology)
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
