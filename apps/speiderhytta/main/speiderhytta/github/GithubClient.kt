package speiderhytta.github

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import libs.http.HttpClientFactory
import libs.utils.appLog
import speiderhytta.GithubConfig
import java.time.Instant

/**
 * Read-only GitHub REST client. Operates against multiple repos:
 *  - Issues: `config.issueRepo` (the team's kanban — `navikt/team-helved`).
 *  - Workflow runs / commits: any code repo passed in per call.
 *
 * The repo argument is explicit on every code-repo call so a single client
 * can serve both `helved-utbetaling` and `helved-peisen` (and any future
 * code repo) without per-repo client instances.
 *
 * Uses snake_case → camelCase property mapping so model fields stay idiomatic Kotlin.
 */
class GithubClient(
    private val config: GithubConfig,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.INFO, json = jacksonConfig),
    private val app: GithubApp = GithubApp(config, client),
) {
    /**
     * List issues with the given labels (AND semantics) updated since `since`.
     * Includes both open and closed issues so resolved incidents are captured.
     * Always queries `config.issueRepo` — incidents live in one place.
     */
    suspend fun issues(labels: List<String>, since: Instant): List<GithubIssue> {
        val url = "${config.apiUrl}/repos/${config.issueRepo}/issues"
        return try {
            client.get(url) {
                bearerAuth(app.token())
                acceptJson()
                parameter("state", "all")
                parameter("labels", labels.joinToString(","))
                parameter("since", since.toString())
                parameter("per_page", 100)
            }.body()
        } catch (t: Throwable) {
            appLog.warn("failed to list github issues for labels=$labels", t)
            emptyList()
        }
    }

    /**
     * Get a single commit from a specific code repo. Used to look up the
     * author timestamp (lead-time = deploy - commit). Caller passes the repo
     * because the same SHA can exist across repos (rare, but the API would
     * 404 on the wrong one).
     */
    suspend fun commit(repo: String, sha: String): GithubCommit? {
        val url = "${config.apiUrl}/repos/$repo/commits/$sha"
        return try {
            client.get(url) {
                bearerAuth(app.token())
                acceptJson()
            }.body()
        } catch (t: Throwable) {
            appLog.warn("failed to fetch github commit repo=$repo sha=$sha", t)
            null
        }
    }

    /**
     * List runs of one workflow file (e.g. `utsjekk.yml`, `deploy.yaml`) on
     * `main` newer than `since`. Only push, dispatch, and chained-workflow
     * events count as deploy attempts. Pagination is capped at one page
     * (100 runs) — at helved's deploy cadence this is several days of data,
     * comfortably more than the poll interval ever needs.
     */
    suspend fun appWorkflowRuns(repo: String, workflowFile: String, since: Instant): List<WorkflowRun> {
        val url = "${config.apiUrl}/repos/$repo/actions/workflows/$workflowFile/runs"
        return try {
            val page: WorkflowRunsPage = client.get(url) {
                bearerAuth(app.token())
                acceptJson()
                parameter("branch", "main")
                parameter("created", ">=${since}")
                parameter("per_page", 100)
            }.body()
            page.workflowRuns.filter { it.event in DEPLOY_EVENTS }
        } catch (t: Throwable) {
            appLog.warn("failed to list github workflow runs repo=$repo file=$workflowFile", t)
            emptyList()
        }
    }

    /**
     * List the jobs of one workflow run. Cheap call (single page) — even the
     * largest helved app workflow has 3-4 jobs.
     */
    suspend fun workflowRunJobs(repo: String, runId: Long): List<WorkflowJob> {
        val url = "${config.apiUrl}/repos/$repo/actions/runs/$runId/jobs"
        return try {
            val page: WorkflowJobsPage = client.get(url) {
                bearerAuth(app.token())
                acceptJson()
                parameter("per_page", 100)
            }.body()
            page.jobs
        } catch (t: Throwable) {
            appLog.warn("failed to list github workflow jobs repo=$repo runId=$runId", t)
            emptyList()
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.acceptJson() {
        headers {
            append(HttpHeaders.Accept, "application/vnd.github+json")
            append("X-GitHub-Api-Version", "2022-11-28")
        }
    }

    companion object {
        private val DEPLOY_EVENTS = setOf("push", "workflow_dispatch", "workflow_run")

        private val jacksonConfig: ObjectMapper.() -> Unit = {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        }
    }
}
