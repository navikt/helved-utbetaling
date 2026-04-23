package speiderhytta.dora

import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import speiderhytta.TestRuntime
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IncidentDaoTest {

    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `upsert refreshes resolution fields`() = runTest(TestRuntime.context) {
        transaction {
            val opened = Instant.parse("2026-04-20T10:00:00Z")
            Incident(githubIssue = 42, app = "utsjekk", title = "boom", openedAt = opened).upsert()

            val resolved = opened.plusSeconds(3600)
            Incident(
                githubIssue = 42, app = "utsjekk", title = "boom (resolved)",
                openedAt = opened, resolvedAt = resolved, mttrSeconds = 3600L,
                causedBySha = "abc1234",
            ).upsert()

            val found = Incident.findByIssue(42)
            assertNotNull(found)
            assertEquals("boom (resolved)", found.title)
            assertEquals(3600L, found.mttrSeconds)
            assertEquals("abc1234", found.causedBySha)
        }
    }

    @Test
    fun `causedBy fields are preserved when subsequent upsert lacks them`() = runTest(TestRuntime.context) {
        transaction {
            val opened = Instant.parse("2026-04-20T10:00:00Z")
            Incident(
                githubIssue = 7, app = "abetal", title = "linked",
                openedAt = opened, causedBySha = "linked-sha",
            ).upsert()

            Incident(
                githubIssue = 7, app = "abetal", title = "linked (no sha this time)",
                openedAt = opened, causedBySha = null,
            ).upsert()

            val found = Incident.findByIssue(7)
            assertNotNull(found)
            assertEquals("linked-sha", found.causedBySha, "COALESCE should retain original sha")
        }
    }

    @Test
    fun `selectFor returns nothing for unknown app`() = runTest(TestRuntime.context) {
        transaction {
            assertEquals(emptyList(), Incident.selectFor("ghost", Instant.EPOCH))
            assertNull(Incident.findByIssue(9999))
        }
    }
}
