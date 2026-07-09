package urskog.oppdrag

import libs.jdbc.await
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import urskog.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DuplicateLinjeTest {

    @AfterEach
    fun cleanup() {
        TestRuntime.mq.reset()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `fingerprint is stored when oppdrag is sent`() {
        val transaction = UUID.randomUUID().toString()
        val sakId = "$seq"
        val oppdrag = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction) { oppdrag }

        TestRuntime.topics.status.assertThat().has(transaction, size = 1)
        assertEquals(1, TestRuntime.mq.sentOppdrag().size)

        val fingerprints = TestRuntime.jdbc.await {
            DaoLinjeFingerprint.query(
                "SELECT * FROM ${DaoLinjeFingerprint.table} WHERE sak_id = ?",
            ) { stmt -> stmt.setString(1, sakId) }
        }
        assertNotNull(fingerprints)
        assertEquals(1, fingerprints.size)
        assertFalse(fingerprints.first().cancelled)
    }

    @Test
    fun `duplicate linje is detected when same period and amount is sent again`() {
        val sakId = "$seq"
        val transaction1 = UUID.randomUUID().toString()
        val transaction2 = UUID.randomUUID().toString()

        val oppdrag1 = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        // First send — establishes fingerprint
        TestRuntime.topics.oppdrag.produce(transaction1) { oppdrag1 }
        TestRuntime.topics.status.assertThat().has(transaction1, size = 1)
        assertEquals(1, TestRuntime.mq.sentOppdrag().size)

        // Second send — same period/amount/klassekode, but different delytelseId (new oppdrag)
        val oppdrag2 = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = PeriodeId().toString(), // new delytelseId
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction2) { oppdrag2 }

        // Still sends (warn-only) — both oppdrag go to MQ
        TestRuntime.topics.status.assertThat().has(transaction2, size = 1)
        assertEquals(2, TestRuntime.mq.sentOppdrag().size)

        // Only one fingerprint row (conflict, no new insert)
        val fingerprints = TestRuntime.jdbc.await {
            DaoLinjeFingerprint.query(
                "SELECT * FROM ${DaoLinjeFingerprint.table} WHERE sak_id = ?",
            ) { stmt -> stmt.setString(1, sakId) }
        }
        assertNotNull(fingerprints)
        assertEquals(1, fingerprints.size)
    }

    @Test
    fun `different amount for same period is not a duplicate`() {
        val sakId = "$seq"
        val transaction1 = UUID.randomUUID().toString()
        val transaction2 = UUID.randomUUID().toString()

        val oppdrag1 = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction1) { oppdrag1 }
        TestRuntime.topics.status.assertThat().has(transaction1, size = 1)

        // Different amount — should NOT be duplicate
        val oppdrag2 = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1500L, // different amount
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction2) { oppdrag2 }
        TestRuntime.topics.status.assertThat().has(transaction2, size = 1)

        val fingerprints = TestRuntime.jdbc.await {
            DaoLinjeFingerprint.query(
                "SELECT * FROM ${DaoLinjeFingerprint.table} WHERE sak_id = ?",
            ) { stmt -> stmt.setString(1, sakId) }
        }
        assertNotNull(fingerprints)
        assertEquals(2, fingerprints.size) // two distinct fingerprints
    }

    @Test
    fun `OPPH marks fingerprint as cancelled, allowing re-send`() {
        val sakId = "$seq"
        val periodeId = PeriodeId().toString()
        val transaction1 = UUID.randomUUID().toString()
        val transaction2 = UUID.randomUUID().toString()
        val transaction3 = UUID.randomUUID().toString()

        // First: send the line
        val oppdrag1 = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = periodeId,
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction1) { oppdrag1 }
        TestRuntime.topics.status.assertThat().has(transaction1, size = 1)

        // Second: OPPH the same line
        val oppdrag2 = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            kodeEndring = "ENDR",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = periodeId,
                    kodeEndring = "ENDR",
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                    opphør = 1.nov,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction2) { oppdrag2 }
        TestRuntime.topics.status.assertThat().has(transaction2, size = 1)

        // Verify fingerprint is cancelled
        val cancelledFingerprints = TestRuntime.jdbc.await {
            DaoLinjeFingerprint.query(
                "SELECT * FROM ${DaoLinjeFingerprint.table} WHERE sak_id = ? AND cancelled = true",
            ) { stmt -> stmt.setString(1, sakId) }
        }
        assertNotNull(cancelledFingerprints)
        assertEquals(1, cancelledFingerprints.size)

        // Third: re-send same period — should NOT be detected as duplicate
        val oppdrag3 = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP",
            kodeEndring = "ENDR",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = PeriodeId().toString(), // new delytelseId
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction3) { oppdrag3 }
        TestRuntime.topics.status.assertThat().has(transaction3, size = 1)

        // Fingerprint reclaimed — cancelled = false
        val reclaimedFingerprints = TestRuntime.jdbc.await {
            DaoLinjeFingerprint.query(
                "SELECT * FROM ${DaoLinjeFingerprint.table} WHERE sak_id = ? AND cancelled = false",
            ) { stmt -> stmt.setString(1, sakId) }
        }
        assertNotNull(reclaimedFingerprints)
        assertEquals(1, reclaimedFingerprints.size)

        // All 3 oppdrag sent to MQ (warn-only, never blocks)
        assertEquals(3, TestRuntime.mq.sentOppdrag().size)
    }
}
