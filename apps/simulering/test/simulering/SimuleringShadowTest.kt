package simulering

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import models.Fagsystem
import models.v2
import org.http4k.core.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Isolated
class SimuleringShadowTest {
    private val events = CopyOnWriteArrayList<ILoggingEvent>()
    private val loggers = listOf("ROOT", "app", "appLog", "ws", "http", "auth", "kafka", "secureLog").map {
        LoggerFactory.getLogger(it) as Logger
    }
    private val capture = object : AppenderBase<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) { events.add(event) }
    }
    private val config = SokosOppdragConfig(host = URI("http://sokos.test").toURL(), scope = "test", cluster = "test-cluster")

    @BeforeEach
    fun setup() {
        capture.start()
        loggers.forEach { it.addAppender(capture) }
    }

    @AfterEach
    fun cleanup() {
        loggers.forEach { it.detachAppender(capture) }
        capture.stop()
    }

    @Test
    fun `REST payloads og feil logges bare sikkert uten bearer token`() {
        val client = SokosClient(config, { Response(Status.BAD_GATEWAY).body("private REST response") }, { "Bearer secret-token" })
        SimuleringShadow(client, config).use { shadow ->
            shadow.submit("private-key", simulering(fagområde = "AAP"), Fagsystem.AAP, v2.Simulering(emptyList()))
            awaitTerminalEvents(1)
        }

        val secure = events.filter { it.loggerName == "secureLog" }.map { it.formattedMessage }
        assertTrue(secure.any { it.contains("12345678910") && it.contains("restRequest") })
        assertTrue(secure.any { it.contains("private REST response") && it.contains("restResponse") })
        assertTrue(secure.any { it.contains("soapMapped") })
        assertTrue(secure.any { it.contains("simulering_shadow_failed") })
        assertTrue(events.filter { it.loggerName != "secureLog" }.none {
            it.formattedMessage.contains("private") || it.formattedMessage.contains("12345678910")
        })
        assertTrue(events.none { it.formattedMessage.contains("secret-token") })
    }

    @Test
    fun `full shadow koe forkaster nye kall uten aa blokkere Kafka arbeidet`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val client = SokosClient(config, {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Response(Status.OK).body("""{"OsSimulerBeregningOperationResponse":{"ZS1OCont":{"responsSimulering":{"statusKode":0}}}}""")
        }, { "Bearer secret-token" })
        SimuleringShadow(client, config).use { shadow ->
            try {
                repeat(32) { shadow.submit("bounded-$it", simulering(fagområde = "AAP"), Fagsystem.AAP, v2.Simulering(emptyList())) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(14, shadowEvents().count { it["event"]?.jsonPrimitive?.content == "simulering_shadow_skipped" })
                assertTrue(shadowEvents().none { it["event"]?.jsonPrimitive?.content == "simulering_shadow_comparison" })
            } finally {
                release.countDown()
                awaitTerminalEvents(32)
            }
        }
    }

    @Test
    fun `SOAP filter logger request og response bare sikkert uten headere`() {
        val handler = SecureLogFilter.then { Response(Status.OK).body("private SOAP response") }
        handler(Request(Method.POST, "http://soap.test")
            .header("X-Proxy-Authorization", "Bearer secret-token")
            .body("private SOAP request"))

        val payloadEvents = events.filter { it.formattedMessage.contains("private SOAP") }
        assertEquals(2, payloadEvents.size)
        assertTrue(payloadEvents.all { it.loggerName == "secureLog" })
        assertTrue(events.none { it.formattedMessage.contains("secret-token") })
    }

    private fun shadowEvents(): List<JsonObject> = events.filter { it.loggerName == "secureLog" }.mapNotNull {
        runCatching { Json.parseToJsonElement(it.formattedMessage).jsonObject }.getOrNull()
    }

    private fun awaitTerminalEvents(count: Int) = runBlocking {
        withTimeout(5.seconds) {
            while (shadowEvents().count {
                it["event"]?.jsonPrimitive?.content in listOf("simulering_shadow_comparison", "simulering_shadow_failed", "simulering_shadow_skipped")
            } < count) delay(10.milliseconds)
        }
    }
}
