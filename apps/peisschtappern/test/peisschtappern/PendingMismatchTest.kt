package peisschtappern

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingMismatchTest {

    private fun utbetalingDao(
        uid: String,
        perioder: List<Map<String, Any?>>,
        systemTimeMs: Long = Instant.now().toEpochMilli(),
        sakId: String? = "sak-1",
        fagsystem: String? = "AAP",
    ) = Daos(
        topic_name = Topics.utbetalinger.name,
        version = "v1",
        key = "key-$uid",
        value = utbetalingJson(uid, perioder),
        partition = 0,
        offset = 0,
        timestamp_ms = systemTimeMs,
        stream_time_ms = systemTimeMs,
        system_time_ms = systemTimeMs,
        trace_id = null,
        sakId = sakId,
        fagsystem = fagsystem,
    )

    private fun pendingDao(
        uid: String,
        perioder: List<Map<String, Any?>>,
        systemTimeMs: Long = Instant.now().toEpochMilli(),
    ) = Daos(
        topic_name = Topics.pendingUtbetalinger.name,
        version = "v1",
        key = "key-$uid",
        value = utbetalingJson(uid, perioder),
        partition = 0,
        offset = 0,
        timestamp_ms = systemTimeMs,
        stream_time_ms = systemTimeMs,
        system_time_ms = systemTimeMs,
        trace_id = null,
    )

    private fun utbetalingJson(uid: String, perioder: List<Map<String, Any?>>): String {
        val periodeJson = perioder.joinToString(",") { p ->
            val vedtakssats = p["vedtakssats"]
            val betalendeEnhet = p["betalendeEnhet"]
            """{"fom":"${p["fom"]}","tom":"${p["tom"]}","beløp":${p["beløp"]},"vedtakssats":${if (vedtakssats == null) "null" else vedtakssats},"betalendeEnhet":${if (betalendeEnhet == null) "null" else "\"$betalendeEnhet\""}}"""
        }
        return """{"uid":"$uid","perioder":[$periodeJson]}"""
    }

    private fun periode(
        fom: String = "2025-01-01",
        tom: String = "2025-01-31",
        beløp: Int = 1000,
        vedtakssats: Int? = null,
        betalendeEnhet: String? = null,
    ) = mapOf("fom" to fom, "tom" to tom, "beløp" to beløp, "vedtakssats" to vedtakssats, "betalendeEnhet" to betalendeEnhet)

    @Test
    fun `ingen mismatch når listene er tomme`() {
        assertTrue(detectMismatches(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `ingen mismatch når perioder er like`() {
        val uid = "abc-123"
        val now = Instant.now().toEpochMilli()
        val perioder = listOf(periode())

        val utbetaling = utbetalingDao(uid, perioder, systemTimeMs = now + 1000)
        val pending = pendingDao(uid, perioder, systemTimeMs = now)

        assertTrue(detectMismatches(listOf(utbetaling), listOf(pending)).isEmpty())
    }

    @Test
    fun `mismatch når beløp er ulikt`() {
        val uid = "abc-123"
        val now = Instant.now().toEpochMilli()

        val utbetaling = utbetalingDao(uid, listOf(periode(beløp = 1000)), systemTimeMs = now + 1000, sakId = "sak-1", fagsystem = "AAP")
        val pending = pendingDao(uid, listOf(periode(beløp = 2000)), systemTimeMs = now)

        val result = detectMismatches(listOf(utbetaling), listOf(pending))

        assertEquals(1, result.size)
        assertEquals(uid, result.first().uid)
        assertEquals("sak-1", result.first().sakId)
        assertEquals("AAP", result.first().fagsystem)
    }

    @Test
    fun `mismatch når perioder har ulikt antall`() {
        val uid = "abc-123"
        val now = Instant.now().toEpochMilli()

        val utbetaling = utbetalingDao(uid, listOf(periode(), periode(fom = "2025-02-01", tom = "2025-02-28")), systemTimeMs = now + 1000)
        val pending = pendingDao(uid, listOf(periode()), systemTimeMs = now)

        assertEquals(1, detectMismatches(listOf(utbetaling), listOf(pending)).size)
    }

    @Test
    fun `ingen mismatch når pending er etter utbetaling`() {
        val uid = "abc-123"
        val now = Instant.now().toEpochMilli()

        val utbetaling = utbetalingDao(uid, listOf(periode(beløp = 1000)), systemTimeMs = now)
        // pending arrives AFTER utbetaling, should not be considered
        val pending = pendingDao(uid, listOf(periode(beløp = 2000)), systemTimeMs = now + 1000)

        assertTrue(detectMismatches(listOf(utbetaling), listOf(pending)).isEmpty())
    }

    @Test
    fun `ingen mismatch når ingen pending finnes for uid`() {
        val uid = "abc-123"
        val utbetaling = utbetalingDao(uid, listOf(periode()))

        assertTrue(detectMismatches(listOf(utbetaling), emptyList()).isEmpty())
    }

    @Test
    fun `bruker nyeste pending som kom før utbetalingen`() {
        val uid = "abc-123"
        val now = Instant.now().toEpochMilli()

        val utbetaling = utbetalingDao(uid, listOf(periode(beløp = 1000)), systemTimeMs = now + 2000)
        // oldest pending - mismatch
        val oldPending = pendingDao(uid, listOf(periode(beløp = 500)), systemTimeMs = now)
        // newest pending before utbetaling - matches
        val newPending = pendingDao(uid, listOf(periode(beløp = 1000)), systemTimeMs = now + 1000)

        assertTrue(detectMismatches(listOf(utbetaling), listOf(oldPending, newPending)).isEmpty())
    }

    @Test
    fun `kun mismatches rapporteres for riktig uid`() {
        val now = Instant.now().toEpochMilli()

        val utbetaling1 = utbetalingDao("uid-1", listOf(periode(beløp = 1000)), systemTimeMs = now + 1000)
        val pending1 = pendingDao("uid-1", listOf(periode(beløp = 2000)), systemTimeMs = now)

        val utbetaling2 = utbetalingDao("uid-2", listOf(periode(beløp = 1000)), systemTimeMs = now + 1000)
        val pending2 = pendingDao("uid-2", listOf(periode(beløp = 1000)), systemTimeMs = now)

        val result = detectMismatches(listOf(utbetaling1, utbetaling2), listOf(pending1, pending2))

        assertEquals(1, result.size)
        assertEquals("uid-1", result.first().uid)
    }
}
