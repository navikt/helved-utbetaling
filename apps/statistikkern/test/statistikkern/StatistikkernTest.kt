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

        val key = "019cc21f-5913-765b-91a4-2f7c07753935"
        val mottattTidMs = 1_700_000_000_000L
        val utbetaling = utbetaling()
        TestRuntime.topics.utbetalinger.populate(
            key,
            utbetaling,
            partition = 0,
            offset = offset.getAndIncrement(),
            timestamp = mottattTidMs
        )

        awaitCondition { TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).isNotEmpty() }

        val row = TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).single()
        assertEquals(utbetaling.originalKey, row["key"].toString())
        assertEquals("AAP", row["fagsystem"].toString())
        assertEquals("AAP_UNDER_ARBEIDSAVKLARING", row["stonad"].toString())
        assertEquals("800", row["belop"].toString())
        assertEquals("2025-01-06", row["fom"].toString())
        assertEquals("2025-01-07", row["tom"].toString())
        assertEquals("2025-01-01T12:00:00", row["vedtakstidspunkt"].toString())
        assertTrue(row["processed_at"].toString().isNotBlank())
        assertTrue(row["inserted_at"].toString().isNotBlank())
    }

    @Test
    fun `utbetaling med flere perioder lagres i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val key = "test-key-flere-perioder"
        val perioder = listOf(
            Utbetalingsperiode(fom = LocalDate.of(2025, 1, 6), tom = LocalDate.of(2025, 1, 7), beløp = 800u),
            Utbetalingsperiode(fom = LocalDate.of(2025, 1, 13), tom = LocalDate.of(2025, 1, 14), beløp = 900u),
            Utbetalingsperiode(fom = LocalDate.of(2025, 1, 20), tom = LocalDate.of(2025, 1, 21), beløp = 1000u),
        )
        val utbetaling = utbetaling(perioder = perioder)
        TestRuntime.topics.utbetalinger.populate(key, utbetaling, partition = 0, offset = offset.getAndIncrement())

        awaitCondition { TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).size == 3 }

        val rows = TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).sortedBy { it["fom"].toString() }
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

        val key = "test-key-dryrun"
        val utbetaling = utbetaling(dryrun = true)
        TestRuntime.topics.utbetalinger.populate(key, utbetaling, partition = 0, offset = offset.getAndIncrement())

        assertTrue(TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).isEmpty())
    }

    @Test
    fun `utbetaling upsert overskriver eksisterende rad ved duplikat`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val key = "test-key-upsert"
        val utbetaling = utbetaling()
        TestRuntime.topics.utbetalinger.populate(key, utbetaling, partition = 0, offset = 100)
        awaitCondition { TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).isNotEmpty() }

        TestRuntime.topics.utbetalinger.populate(key, utbetaling, partition = 0, offset = 100)

        assertEquals(1, TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).size)
    }

    @Test
    fun `status lagres i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val key = "test-uid-status"
        TestRuntime.topics.status.populate(key, StatusReply.ok(), partition = 0, offset = offset.getAndIncrement())

        awaitCondition { TestRuntime.bq.queryStatus(key).isNotEmpty() }

        val row = TestRuntime.bq.queryStatus(key).single()
        assertEquals(key, row["key"].toString())
        assertEquals("OK", row["status"].toString())
    }

    @Test
    fun `tombstone på status-topic hoppes over`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val key = "test-uid-tombstone"
        TestRuntime.topics.status.populate(key, null, partition = 0, offset = offset.getAndIncrement())

        assertTrue(TestRuntime.bq.queryStatus(key).isEmpty())
    }
}

private suspend fun awaitCondition(
    timeout: kotlin.time.Duration = 10.seconds,
    condition: () -> Boolean,
) = withTimeout(timeout) {
    while (!condition()) delay(50)
}
