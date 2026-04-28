package utsjekk

import TestRuntime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.fail
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class StartupValidationTest {

    @Test
    fun `bad db url fails fast`() = runTest {
        val config = TestRuntime.config.copy(
            jdbc = TestRuntime.config.jdbc.copy(
                url = "jdbc:postgresql://127.0.0.1:1/utsjekk?connectTimeout=1&socketTimeout=1"
            ),
            startupValidationTimeoutSeconds = 5,
        )
        val started = TimeSource.Monotonic.markNow()

        val error = assertSuspendsWith<StartupValidationException> {
            validateStartupConfig(
                config,
                timeout = 5.seconds,
                dependencies = StartupValidationDependencies(
                    validateKafka = {},
                    validateExternalServices = {},
                ),
            )
        }

        assertTrue(started.elapsedNow() < 5.seconds)
        assertTrue(error.message.contains("database connectivity check failed"))
    }

    @Test
    fun `bad kafka brokers fails fast`() = runTest {
        val config = TestRuntime.config.copy(
            kafka = TestRuntime.config.kafka.copy(
                brokers = "127.0.0.1:1",
                ssl = null,
            ),
            startupValidationTimeoutSeconds = 2,
        )
        val started = TimeSource.Monotonic.markNow()

        val error = assertSuspendsWith<StartupValidationException> {
            validateStartupConfig(
                config,
                timeout = 2.seconds,
                dependencies = StartupValidationDependencies(
                    validateDb = {},
                    validateExternalServices = {},
                ),
            )
        }

        assertTrue(started.elapsedNow() < 6.seconds)
        assertTrue(
            error.message.contains("timed out after 2s") ||
                error.message.contains("kafka cluster check failed")
        )
    }

    @Test
    fun `bad external host fails fast`() = runTest {
        val config = TestRuntime.config.copy(
            simulering = TestRuntime.config.simulering.copy(
                host = URI("http://127.0.0.1:1").toURL(),
            ),
            startupValidationTimeoutSeconds = 5,
        )
        val started = TimeSource.Monotonic.markNow()

        val error = assertSuspendsWith<StartupValidationException> {
            validateStartupConfig(
                config,
                timeout = 5.seconds,
                dependencies = StartupValidationDependencies(
                    validateDb = {},
                    validateKafka = {},
                ),
            )
        }

        assertTrue(started.elapsedNow() < 5.seconds)
        assertTrue(error.message.contains("external service probe failed for simulering"))
    }

    @Test
    fun `happy startup completes under 5s`() = runTest {
        val config = TestRuntime.config
        val started = TimeSource.Monotonic.markNow()

        validateStartupConfig(
            config,
            timeout = 5.seconds,
            dependencies = StartupValidationDependencies(
                validateKafka = {},
            ),
        )

        assertTrue(started.elapsedNow() < 5.seconds)
    }
}

private suspend inline fun <reified T : Throwable> assertSuspendsWith(noinline block: suspend () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        assertTrue(error is T, "Expected ${T::class.simpleName}, got ${error::class.simpleName}")
        return error
    }

    fail("Expected ${T::class.simpleName} to be thrown")
}
