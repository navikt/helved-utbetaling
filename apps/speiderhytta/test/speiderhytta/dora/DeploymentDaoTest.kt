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

class DeploymentDaoTest {

    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `insert is idempotent on (app, sha, env, run_id)`() = runTest(TestRuntime.context) {
        transaction {
            val d = sample(sha = "abc1234", runId = 42L)
            assertEquals(1, d.insert())
            assertEquals(0, d.insert(), "second insert with same run_id should no-op via ON CONFLICT")
            val found = Deployment.findBySha("utsjekk", "abc1234", "prod-gcp")
            assertNotNull(found)
            assertEquals(120L, found.leadTimeSeconds)
            assertEquals("success", found.outcome)
            assertEquals(42L, found.runId)
        }
    }

    @Test
    fun `re-run of same sha produces a second row`() = runTest(TestRuntime.context) {
        transaction {
            val now = Instant.parse("2026-04-22T12:00:00Z")
            sample(sha = "abc1234", runId = 1L, outcome = "failure", finished = now.minusSeconds(600)).insert()
            sample(sha = "abc1234", runId = 2L, outcome = "success", finished = now).insert()

            val all = Deployment.selectAllFor("utsjekk", "prod-gcp", now.minusSeconds(3600))
            assertEquals(2, all.size, "both attempts should be stored as separate rows")

            // findBySha returns the most recent attempt — the successful re-run.
            val latest = Deployment.findBySha("utsjekk", "abc1234", "prod-gcp")
            assertNotNull(latest)
            assertEquals("success", latest.outcome)
            assertEquals(2L, latest.runId)
        }
    }

    @Test
    fun `selectSuccessfulFor filters out failures and cancellations`() = runTest(TestRuntime.context) {
        transaction {
            val now = Instant.parse("2026-04-22T12:00:00Z")
            sample(sha = "ok", runId = 1L, outcome = "success", finished = now.minusSeconds(60)).insert()
            sample(sha = "bad", runId = 2L, outcome = "failure", finished = now.minusSeconds(120)).insert()
            sample(sha = "stop", runId = 3L, outcome = "cancelled", finished = now.minusSeconds(180)).insert()

            val successOnly = Deployment.selectSuccessfulFor("utsjekk", "prod-gcp", now.minusSeconds(3600))
            assertEquals(1, successOnly.size)
            assertEquals("ok", successOnly.first().sha)

            val all = Deployment.selectAllFor("utsjekk", "prod-gcp", now.minusSeconds(3600))
            assertEquals(3, all.size)
        }
    }

    @Test
    fun `mostRecentBetween picks newest successful deploy in window`() = runTest(TestRuntime.context) {
        transaction {
            val now = Instant.parse("2026-04-22T12:00:00Z")
            sample(sha = "old", runId = 1L, finished = now.minusSeconds(7200)).insert()
            sample(sha = "newer", runId = 2L, finished = now.minusSeconds(60)).insert()
            sample(sha = "newest-but-failed", runId = 3L, outcome = "failure", finished = now.minusSeconds(30)).insert()
            sample(sha = "different-app", app = "abetal", runId = 4L, finished = now.minusSeconds(20)).insert()

            val hit = Deployment.mostRecentBetween(
                app = "utsjekk",
                env = "prod-gcp",
                windowStart = now.minusSeconds(3600),
                windowEnd = now,
            )
            assertNotNull(hit)
            assertEquals("newer", hit.sha, "failed deploy should not be linkable as cause")
        }
    }

    @Test
    fun `selectSuccessfulFor honours since filter`() = runTest(TestRuntime.context) {
        transaction {
            val now = Instant.parse("2026-04-22T12:00:00Z")
            sample(sha = "ancient", runId = 1L, finished = now.minusSeconds(86_400 * 60)).insert()
            sample(sha = "recent", runId = 2L, finished = now.minusSeconds(3600)).insert()

            val rows = Deployment.selectSuccessfulFor("utsjekk", "prod-gcp", now.minusSeconds(86_400))
            assertEquals(1, rows.size)
            assertEquals("recent", rows.first().sha)

            assertNull(Deployment.findBySha("utsjekk", "missing", "prod-gcp"))
        }
    }

    private fun sample(
        app: String = "utsjekk",
        sha: String = "deadbee",
        env: String = "prod-gcp",
        finished: Instant = Instant.parse("2026-04-22T12:00:00Z"),
        leadSeconds: Long = 120L,
        outcome: String = "success",
        runId: Long = 1L,
    ) = Deployment(
        app = app,
        sha = sha,
        env = env,
        commitTs = finished.minusSeconds(leadSeconds),
        deployStartedTs = finished.minusSeconds(60),
        deployFinishedTs = finished,
        leadTimeSeconds = leadSeconds,
        outcome = outcome,
        runId = runId,
        runUrl = null,
    )
}
