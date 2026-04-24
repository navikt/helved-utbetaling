package speiderhytta.dora

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import speiderhytta.Metrics
import speiderhytta.TestRuntime
import speiderhytta.github.GithubIssue
import speiderhytta.github.GithubLabel
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IncidentServiceTest {

    private val metrics = Metrics(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))

    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `explicit Caused-by line links to the matching deployment`() = runTest(TestRuntime.context) {
        val opened = Instant.parse("2026-04-22T12:00:00Z")
        val sha = "abc1234"

        transaction {
            seedDeployment(sha = sha, finished = opened.minusSeconds(600))
        }

        val service = IncidentService(
            issues = { _, _ -> listOf(issue(123, body = "thing broke\n\nCaused-by: $sha\n", openedAt = opened)) },
            metrics = metrics,
            jdbcCtx = TestRuntime.context,
        )

        service.ingest(opened.minusSeconds(3600))

        val stored = transaction { Incident.findByIssue(123) }
        assertNotNull(stored)
        assertEquals(sha, stored.causedBySha)
        assertNotNull(stored.causedByDeploymentId)
    }

    @Test
    fun `heuristic falls back to most-recent deploy in 24h window`() = runTest(TestRuntime.context) {
        val opened = Instant.parse("2026-04-22T12:00:00Z")

        transaction {
            seedDeployment(sha = "older", finished = opened.minusSeconds(60_000))
            seedDeployment(sha = "newest", finished = opened.minusSeconds(900))
        }

        val service = IncidentService(
            issues = { _, _ -> listOf(issue(7, body = "no caused-by here", openedAt = opened)) },
            metrics = metrics,
            jdbcCtx = TestRuntime.context,
        )
        service.ingest(opened.minusSeconds(3600))

        val stored = transaction { Incident.findByIssue(7) }
        assertNotNull(stored)
        assertEquals("newest", stored.causedBySha)
    }

    @Test
    fun `no link when no deploy in window and no Caused-by`() = runTest(TestRuntime.context) {
        val opened = Instant.parse("2026-04-22T12:00:00Z")

        val service = IncidentService(
            issues = { _, _ -> listOf(issue(99, body = null, openedAt = opened)) },
            metrics = metrics,
            jdbcCtx = TestRuntime.context,
        )
        service.ingest(opened.minusSeconds(3600))

        val stored = transaction { Incident.findByIssue(99) }
        assertNotNull(stored)
        assertNull(stored.causedBySha)
        assertNull(stored.causedByDeploymentId)
    }

    @Test
    fun `closed issue gets mttr in seconds`() = runTest(TestRuntime.context) {
        val opened = Instant.parse("2026-04-22T10:00:00Z")
        val closed = opened.plusSeconds(45 * 60)

        val service = IncidentService(
            issues = { _, _ -> listOf(issue(11, body = null, openedAt = opened, closedAt = closed)) },
            metrics = metrics,
            jdbcCtx = TestRuntime.context,
        )
        service.ingest(opened.minusSeconds(3600))

        val stored = transaction { Incident.findByIssue(11) }
        assertNotNull(stored)
        assertEquals(45L * 60, stored.mttrSeconds)
        assertEquals(closed, stored.resolvedAt)
    }

    @Test
    fun `issue without app label is skipped`() = runTest(TestRuntime.context) {
        val opened = Instant.parse("2026-04-22T12:00:00Z")
        val service = IncidentService(
            issues = { _, _ ->
                listOf(
                    GithubIssue(
                        number = 1, title = "no app label", state = "open",
                        body = null, labels = listOf(GithubLabel("incident")),
                        createdAt = opened, closedAt = null,
                    )
                )
            },
            metrics = metrics,
            jdbcCtx = TestRuntime.context,
        )
        service.ingest(opened.minusSeconds(3600))
        val stored = transaction { Incident.findByIssue(1) }
        assertNull(stored, "issue without app: label should be ignored")
    }

    private fun issue(
        number: Long,
        body: String?,
        openedAt: Instant,
        closedAt: Instant? = null,
        app: String = "utsjekk",
    ) = GithubIssue(
        number = number,
        title = "test issue $number",
        state = if (closedAt == null) "open" else "closed",
        body = body,
        labels = listOf(GithubLabel("incident"), GithubLabel("app:$app")),
        createdAt = openedAt,
        closedAt = closedAt,
    )

    private suspend fun seedDeployment(sha: String, finished: Instant, app: String = "utsjekk", runId: Long = sha.hashCode().toLong()) {
        Deployment(
            app = app, sha = sha, env = "prod-gcp",
            commitTs = finished.minusSeconds(120),
            deployStartedTs = finished.minusSeconds(60),
            deployFinishedTs = finished,
            leadTimeSeconds = 120L,
            outcome = "success",
            runId = runId,
            runUrl = null,
        ).insert()
    }
}
