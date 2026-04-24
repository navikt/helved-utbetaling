package speiderhytta.dora

import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import speiderhytta.Metrics
import speiderhytta.github.GithubClient
import speiderhytta.github.GithubIssue
import java.time.Duration
import java.time.Instant

/**
 * Source of incident issues. In production this delegates to [GithubClient];
 * in tests we pass a literal list so we don't need a fake GitHub server.
 */
fun interface IssueFetcher {
    suspend fun fetch(labels: List<String>, since: Instant): List<GithubIssue>
}

fun GithubClient.asFetcher(): IssueFetcher = IssueFetcher { labels, since -> issues(labels, since) }

/**
 * Pulls incident issues from GitHub and links each to the deployment that
 * (likely) caused it.
 *
 * Linking strategy:
 *  1. **Explicit:** the issue body contains a line like `Caused-by: <sha>`.
 *  2. **Heuristic fallback:** the most recent prod deployment of the same app
 *     in the 24 hours before `opened_at`.
 *
 * Issues that are still open have `resolved_at` and `mttr_seconds` left null.
 */
class IncidentService(
    private val issues: IssueFetcher,
    private val metrics: Metrics,
    private val jdbcCtx: CoroutineDatasource,
    private val targetEnv: String = "prod-gcp",
    private val heuristicWindow: Duration = Duration.ofHours(24),
) {
    suspend fun ingest(since: Instant): Instant {
        val fetched = issues.fetch(labels = listOf("incident"), since = since)
        if (fetched.isEmpty()) return since

        var newCursor = since
        for (issue in fetched) {
            val app = appLabel(issue) ?: continue
            val incident = withContext(jdbcCtx) {
                transaction {
                    val link = link(app, issue)
                    Incident(
                        githubIssue = issue.number,
                        app = app,
                        title = issue.title,
                        openedAt = issue.createdAt,
                        resolvedAt = issue.closedAt,
                        mttrSeconds = mttr(issue),
                        causedBySha = link?.sha,
                        causedByDeploymentId = link?.deploymentId,
                    ).also {
                        val rows = it.upsert()
                        if (rows > 0) {
                            appLog.info("recorded incident issue={} app={}", it.githubIssue, it.app)
                            metrics.incidentObserved()
                        }
                    }
                }
            }
            val touched = incident.resolvedAt ?: incident.openedAt
            if (touched.isAfter(newCursor)) newCursor = touched
        }
        return newCursor
    }

    private fun appLabel(issue: GithubIssue): String? =
        issue.labels.map(speiderhytta.github.GithubLabel::name)
            .firstOrNull { it.startsWith("app:") }
            ?.removePrefix("app:")

    private fun mttr(issue: GithubIssue): Long? =
        issue.closedAt?.let { (it.epochSecond - issue.createdAt.epochSecond).coerceAtLeast(0) }

    private suspend fun link(app: String, issue: GithubIssue): Link? {
        explicitSha(issue.body)?.let { sha ->
            Deployment.findBySha(app, sha, targetEnv)?.let { d ->
                return Link(sha = sha, deploymentId = d.id)
            }
            return Link(sha = sha, deploymentId = null)
        }
        val windowStart = issue.createdAt.minus(heuristicWindow)
        val candidate = Deployment.mostRecentBetween(app, targetEnv, windowStart, issue.createdAt)
            ?: return null
        return Link(sha = candidate.sha, deploymentId = candidate.id)
    }

    private fun explicitSha(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val match = CAUSED_BY.find(body) ?: return null
        return match.groupValues[1]
    }

    private data class Link(val sha: String?, val deploymentId: Long?)

    companion object {
        private val CAUSED_BY = Regex("(?im)^\\s*Caused-by:\\s*([0-9a-f]{7,40})\\s*$")
    }
}
