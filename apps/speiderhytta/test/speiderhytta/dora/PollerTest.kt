package speiderhytta.dora

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PollerTest {
    private val current = Instant.parse("2026-09-21T12:00:00Z")

    @Test
    fun `standardpoller flytter bare cursor fremover`() {
        assertTrue(shouldSaveCursor(current, current.plusSeconds(1), allowRewind = false))
        assertFalse(shouldSaveCursor(current, current, allowRewind = false))
        assertFalse(shouldSaveCursor(current, current.minusSeconds(1), allowRewind = false))
    }

    @Test
    fun `workflow-poller kan flytte cursor bakover til uferdig run`() {
        assertTrue(shouldSaveCursor(current, current.minusSeconds(1), allowRewind = true))
    }
}
