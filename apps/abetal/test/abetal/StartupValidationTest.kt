package abetal

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import libs.kafka.StreamsMock
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class StartupValidationTest {

    @Test
    fun `bad kafka brokers fails fast`() {
        val kafka = StreamsMock()
        val config = Config(
            kafka = kafka.config.copy(
                applicationId = "startup-validation-${UUID.randomUUID()}",
                brokers = "127.0.0.1:1",
                ssl = null,
            ),
        )

        val mark = TimeSource.Monotonic.markNow()

        val error = assertFailsWith<IllegalStateException> {
            runBlocking {
                validateStartupConfig(config, timeout = 2.seconds)
            }
        }

        val duration = mark.elapsedNow()

        assertTrue(error.message?.contains("timed out") == true || error.message?.contains("Kafka") == true)
        assertTrue(duration < 5.seconds, "Expected kafka validation failure under 5s, got $duration")
    }

    @Test
    fun `happy startup completes under 5s`() {
        val kafka = StreamsMock()
        val config = Config(kafka = kafka.config.copy(applicationId = "startup-validation-${UUID.randomUUID()}"))

        val mark = TimeSource.Monotonic.markNow()

        runBlocking {
            validateStartupConfig(
                config,
                timeout = 5.seconds,
                checks = StartupChecks(
                    kafka = {
                        delay(100)
                    }
                )
            )
        }

        val duration = mark.elapsedNow()

        assertTrue(duration < 5.seconds, "Expected startup validation under 5s, got $duration")
    }
}
