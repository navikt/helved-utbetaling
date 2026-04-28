package abetal

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import libs.kafka.StreamsMock
import libs.ktor.KtorRuntime
import libs.utils.appLog
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import org.slf4j.LoggerFactory
import java.util.Properties

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
    val historiskIntern = kafka.testTopic(Topics.historiskIntern)
    val retryOppdrag = kafka.testTopic(Topics.retryOppdrag)
    val dryrunAap = kafka.testTopic(Topics.dryrunAap)
    val dryrunDp = kafka.testTopic(Topics.dryrunDp)
    val dryrunTs = kafka.testTopic(Topics.dryrunTs)
    val dryrunTp = kafka.testTopic(Topics.dryrunTp)
}

object TestRuntime {
    val kafka: StreamsMock = StreamsMock()
    val logAppender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>().apply { start() }
    val config = Config(
        kafka = kafka.config.copy(additionalProperties = Properties().apply {
            this[org.apache.kafka.streams.StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG] =
                StatusOnProcessingErrorHandler::class.java
            put("state.dir", "build/kafka-streams")
            put("max.task.idle.ms", -1L)
            put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
        })
    )
    init {
        (LoggerFactory.getLogger(appLog.name) as Logger).apply {
            if (!isAttached(logAppender)) addAppender(logAppender)
        }
        KtorRuntime<Config>(
            appName = "abetal",
            module = {
                abetal(
                    config = config,
                    kafka = kafka,
                    topology = createTopology(kafka),
                )
            }
        )
    }
    val topics: TestTopics = TestTopics(kafka)

    fun clearLogs() = logAppender.list.clear()
}

private fun Logger.isAttached(appender: ListAppender<ILoggingEvent>): Boolean =
    iteratorForAppenders().asSequence().any { it === appender }
