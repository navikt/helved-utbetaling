package statistikkern

import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import models.StatusReply
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StatistikkernTest {

    @BeforeEach
    fun reset() {
        TestRuntime.kafka.reset()
    }

    companion object {
        private val offset = AtomicLong(0)
    }

    @Test
    fun `utbetaling lagres i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val uid = "test-uid-utbetaling"
        TestRuntime.topics.utbetalinger.populate(uid, utbetaling(), partition = 0, offset = offset.getAndIncrement())

        awaitCondition { TestRuntime.bq.queryUtbetalinger(uid).isNotEmpty() }

        val row = TestRuntime.bq.queryUtbetalinger(uid).single()
        assertEquals(uid, row["uid"].toString())
        assertEquals("AAP", row["fagsystem"].toString())
        assertEquals("SAK-123", row["sak_id"].toString())
        assertEquals("800", row["total_belop"].toString())
    }

    @Test
    fun `dryrun utbetaling lagres ikke i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val uid = "test-uid-dryrun"
        TestRuntime.topics.utbetalinger.populate(uid, utbetaling(dryrun = true), partition = 0, offset = offset
            .getAndIncrement())

        assertTrue(TestRuntime.bq.queryUtbetalinger(uid).isEmpty())
    }

    @Test
    fun `utbetaling upsert overskriver eksisterende rad ved duplikat`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val uid = "test-uid-upsert"
        TestRuntime.topics.utbetalinger.populate(uid, utbetaling(), partition = 0, offset = 100)
        awaitCondition { TestRuntime.bq.queryUtbetalinger(uid).isNotEmpty() }

        TestRuntime.topics.utbetalinger.populate(uid, utbetaling(), partition = 0, offset = 100)

        assertEquals(1, TestRuntime.bq.queryUtbetalinger(uid).size)
    }

    @Test
    fun `status lagres i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val uid = "test-uid-status"
        TestRuntime.topics.status.populate(uid, StatusReply.ok(), partition = 0, offset = offset.getAndIncrement())

        awaitCondition { TestRuntime.bq.queryStatus(uid).isNotEmpty() }

        val row = TestRuntime.bq.queryStatus(uid).single()
        assertEquals(uid, row["uid"].toString())
        assertEquals("OK", row["status"].toString())
    }

    @Test
    fun `tombstone på status-topic hoppes over`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val uid = "test-uid-tombstone"
        TestRuntime.topics.status.populate(uid, null, partition = 0, offset = offset.getAndIncrement())

        assertTrue(TestRuntime.bq.queryStatus(uid).isEmpty())
    }
}

private suspend fun awaitCondition(
    timeout: kotlin.time.Duration = 10.seconds,
    condition: () -> Boolean,
) = withTimeout(timeout) {
    while (!condition()) delay(50)
}