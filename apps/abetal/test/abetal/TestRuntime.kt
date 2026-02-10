package abetal

import libs.kafka.StreamsMock
import libs.ktor.KtorRuntime
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import java.util.*
import libs.utils.logger

val testLog = logger("test")

class TestTopics(kafka: StreamsMock) {
    val aap = kafka.testTopic(Topics.aap)
    val dp = kafka.testTopic(Topics.dp)
    val ts = kafka.testTopic(Topics.ts) 
    val tp = kafka.testTopic(Topics.tp)
    val historisk = kafka.testTopic(Topics.historisk)
    val saker = kafka.testTopic(Topics.saker) 
    val utbetalinger = kafka.testTopic(Topics.utbetalinger) 
    val oppdrag = kafka.testTopic(Topics.oppdrag)
    val status = kafka.testTopic(Topics.status)
    val simulering = kafka.testTopic(Topics.simulering) 
    val pendingUtbetalinger = kafka.testTopic(Topics.pendingUtbetalinger) 
    val dpIntern = kafka.testTopic(Topics.dpIntern) 
    val tsIntern = kafka.testTopic(Topics.tsIntern)
    val historiskIntern = kafka.testTopic(Topics.historisk)
    val retryOppdrag = kafka.testTopic(Topics.retryOppdrag)
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
                topology = createTopology(kafka)
            )
        }
    )
    val topics: TestTopics = TestTopics(kafka)
}

