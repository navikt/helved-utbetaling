package abetal

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import libs.kafka.StreamsMock
import libs.ktor.*
import libs.utils.*
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import java.util.*

class TestTopics(private val kafka: StreamsMock) {
    val dp = kafka.testTopic(Topics.dp)
    val saker = kafka.testTopic(Topics.saker) 
    val utbetalinger = kafka.testTopic(Topics.utbetalinger) 
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
    val status = kafka.testTopic(Topics.status) 
    val simulering = kafka.testTopic(Topics.simulering) 
    val pendingUtbetalinger = kafka.testTopic(Topics.pendingUtbetalinger) 
    val fk = kafka.testTopic(Topics.fk) 
}

object TestRuntime {
    val kafka: StreamsMock = StreamsMock()
    val config = Config(
        kafka = kafka.config.copy(additionalProperties = Properties().apply {
            put("state.dir", "build/kafka-streams")
            put("max.task.idle.ms", -1L)
            put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
        })
    )
    val ktor = KtorRuntime<Config>(
        appName = "abetal",
        module = {
            abetal(
                config = config, 
                kafka = kafka, 
                topology = kafka.append(createTopology()) {
                    consume(Tables.saker)
                }
            )
        },
    )
    val topics: TestTopics = TestTopics(kafka)
}

