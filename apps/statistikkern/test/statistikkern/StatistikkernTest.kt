package statistikkern

import io.ktor.server.testing.testApplication
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import models.StatusReply
import models.Utbetalingsperiode
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
        assertEquals("800", row["belop"].toString())
        assertEquals("2025-01-06", row["fom"].toString())
        assertEquals("2025-01-07", row["tom"].toString())
        assertTrue(row["inserted_at"].toString().isNotBlank())
    }

    @Test
    fun `utbetaling med flere perioder lagres i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val uid = "test-uid-flere-perioder"
        val perioder = listOf(
            Utbetalingsperiode(fom = LocalDate.of(2025, 1, 6), tom = LocalDate.of(2025, 1, 7), beløp = 800u),
            Utbetalingsperiode(fom = LocalDate.of(2025, 1, 13), tom = LocalDate.of(2025, 1, 14), beløp = 900u),
            Utbetalingsperiode(fom = LocalDate.of(2025, 1, 20), tom = LocalDate.of(2025, 1, 21), beløp = 1000u),
        )
        TestRuntime.topics.utbetalinger.populate(uid, utbetaling(perioder = perioder), partition = 0, offset = offset.getAndIncrement())

        awaitCondition { TestRuntime.bq.queryUtbetalinger(uid).size == 3 }

        val rows = TestRuntime.bq.queryUtbetalinger(uid).sortedBy { it["fom"].toString() }
        assertEquals(3, rows.size)

        assertEquals("2025-01-06", rows[0]["fom"].toString())
        assertEquals("2025-01-07", rows[0]["tom"].toString())
        assertEquals("800", rows[0]["belop"].toString())

        assertEquals("2025-01-13", rows[1]["fom"].toString())
        assertEquals("2025-01-14", rows[1]["tom"].toString())
        assertEquals("900", rows[1]["belop"].toString())

        assertEquals("2025-01-20", rows[2]["fom"].toString())
        assertEquals("2025-01-21", rows[2]["tom"].toString())
        assertEquals("1000", rows[2]["belop"].toString())
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