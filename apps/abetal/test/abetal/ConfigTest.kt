package abetal

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ConfigTest {

    @Test
    fun `kafka consumer config har eksplisitte hardening overrides`() {
        val configSource = java.io.File("main/abetal/Config.kt").readText()

        val maxPoll = extractIntAssign(configSource, "MAX_POLL_INTERVAL_MS_CONFIG")
        val sessionTimeout = extractIntAssign(configSource, "SESSION_TIMEOUT_MS_CONFIG")
        val heartbeat = extractIntAssign(configSource, "HEARTBEAT_INTERVAL_MS_CONFIG")
        val isolation = extractStringAssign(configSource, "ISOLATION_LEVEL_CONFIG")

        assertEquals(600_000, maxPoll, "MAX_POLL_INTERVAL_MS_CONFIG må være 600_000")
        assertEquals(30_000, sessionTimeout, "SESSION_TIMEOUT_MS_CONFIG må være 30_000")
        assertEquals(10_000, heartbeat, "HEARTBEAT_INTERVAL_MS_CONFIG må være 10_000")
        assertEquals("read_committed", isolation, "ISOLATION_LEVEL_CONFIG må være read_committed")

        assertEquals(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "max.poll.interval.ms")
    }

    private fun extractIntAssign(source: String, propertyName: String): Int {
        val regex = Regex("""ConsumerConfig\.$propertyName\]\s*=\s*([0-9_]+)""")
        val raw = regex.find(source)?.groupValues?.get(1)
            ?: error("fant ikke $propertyName i Config.kt")
        return raw.replace("_", "").toInt()
    }

    private fun extractStringAssign(source: String, propertyName: String): String {
        val regex = Regex("""ConsumerConfig\.$propertyName\]\s*=\s*"([^"]+)"""")
        return regex.find(source)?.groupValues?.get(1)
            ?: error("fant ikke $propertyName i Config.kt")
    }
}
