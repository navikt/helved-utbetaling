package abetal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.kafka.StreamsMock
import libs.utils.*
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import java.util.*

private val testLog = logger("test")

class TestTopics(private val kafka: StreamsMock) {
    val dp = kafka.testTopic(Topics.dp)
    val saker = kafka.testTopic(Topics.saker) 
    val utbetalinger = kafka.testTopic(Topics.utbetalinger) 
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val status = kafka.testTopic(Topics.status) 
    val simulering = kafka.testTopic(Topics.simulering) 
}

object TestRuntime {
    val ktor: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    val kafka: StreamsMock
    val topics: TestTopics

    init {
        kafka = StreamsMock()

        val topology = kafka.append(createTopology()) {
            consume(Tables.saker)
        }

        ktor = embeddedServer(Netty, port = 0) {
            val config = Config(
                kafka = kafka.config.copy(additionalProperties = Properties().apply {
                    put("state.dir", "build/kafka-streams")
                    put("max.task.idle.ms", -1L)
                    put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
                })
            )
            abetal(config, TestRuntime.kafka, topology)
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down TestRunner")
            ktor.stop(1000L, 5000L)
        })

        ktor.start(wait = false)
        topics = TestTopics(kafka)
    }
}

val httpClient: HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    defaultRequest {
        url("http://localhost:${TestRuntime.ktor.engine.port}")
    }
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

