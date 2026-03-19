package statistikkern

import io.ktor.server.testing.testApplication
import java.time.LocalDate
import java.util.UUID
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

        val mottattTidMs = 1_700_000_000_000L
        val originalKey = UUID.randomUUID().toString()
        val utbetaling = utbetaling(originalKey = originalKey)
        TestRuntime.topics.utbetalinger.populate(
            UUID.randomUUID().toString(),
            utbetaling,
            partition = 0,
            offset = offset.getAndIncrement(),
            timestamp = mottattTidMs,
            headers = mapOf("x-sy" to "1700000000000")
        )

        awaitCondition { TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).isNotEmpty() }

        val row = TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).single()
        assertEquals(utbetaling.originalKey, row["key"].toString())
        assertEquals("AAP", row["fagsystem"].toString())
        assertEquals("AAP_UNDER_ARBEIDSAVKLARING", row["stonad"].toString())
        assertEquals("800", row["belop"].toString())
        assertTrue(row["sendt"].toString().isNotBlank())
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
        TestRuntime.topics.utbetalinger.populate(key, utbetaling, partition = 0, offset = offset.getAndIncrement(), headers = mapOf("x-sy" to "1700000000000"))

        awaitCondition { TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).size == 3 }

        val rows = TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey)
        assertEquals(3, rows.size)

        assertEquals("800", rows[0]["belop"].toString())
        assertEquals("900", rows[1]["belop"].toString())
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
        TestRuntime.topics.utbetalinger.populate(key, utbetaling, partition = 0, offset = 100, headers = mapOf("x-sy" to "1700000000000")
        )
        awaitCondition { TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).isNotEmpty() }

        TestRuntime.topics.utbetalinger.populate(key, utbetaling, partition = 0, offset = 100)

        assertEquals(1, TestRuntime.bq.queryUtbetalinger(utbetaling.originalKey).size)
    }

    @Test
    fun `status lagres i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val key = "test-uid-status"
        TestRuntime.topics.status.populate(
            key, StatusReply.ok(),
            partition = 0,
            offset = offset.getAndIncrement(),
            headers = mapOf("x-sy" to "1700000000000")
        )

        awaitCondition { TestRuntime.bq.queryStatus(key).isNotEmpty() }

        val row = TestRuntime.bq.queryStatus(key).single()
        assertEquals(key, row["key"].toString())
        assertEquals("OK", row["status"].toString())
        assertTrue(row["sendt"].toString().isNotBlank())
    }

    @Test
    fun `tombstone på status-topic hoppes over`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val key = "test-uid-tombstone"
        TestRuntime.topics.status.populate(key, null, partition = 0, offset = offset.getAndIncrement())

        assertTrue(TestRuntime.bq.queryStatus(key).isEmpty())
    }

    @Test
    fun `oppdrag lagres i bigquery`() = testApplication {
        application { statistikkern(TestRuntime.config, TestRuntime.kafka, TestRuntime.bq, TestRuntime.kafka) }

        val sakId = "SAK-${UUID.randomUUID()}"
        val oppdrag = oppdrag(sakId = sakId)
        TestRuntime.topics.oppdrag.populate(sakId, oppdrag, partition = 0, offset = offset.getAndIncrement())

        awaitCondition { TestRuntime.bq.queryOppdrag(sakId).isNotEmpty() }

        val row = TestRuntime.bq.queryOppdrag(sakId).single()
        assertEquals("BEH-456", row["behandling"].toString())
        assertEquals(sakId, row["sak"].toString())
        assertEquals("2025-01-01-10.10.00.000000", row["kvittert"].toString())
        assertEquals("AAP", row["fagområde"].toString())
    }
}

private suspend fun awaitCondition(
    timeout: kotlin.time.Duration = 10.seconds,
    condition: () -> Boolean,
) = withTimeout(timeout) {
    while (!condition()) delay(50)
}
