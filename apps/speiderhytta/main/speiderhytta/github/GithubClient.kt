package speiderhytta.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import libs.utils.appLog
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import speiderhytta.GithubConfig
import java.time.Instant
import java.net.URI
import java.util.Base64

/**
 * GitHub REST client. Reads observability and audit data across code repos,
 * and writes Speiderhytta's managed audit comment in the issue repo.
 *  - Issues: `config.issueRepo` (the team's kanban — `navikt/team-helved`).
 *  - Workflow runs / commits: any code repo passed in per call.
 *
 * The repo argument is explicit on every code-repo call so a single client
 * can serve both `helved-utbetaling` and `helved-peisen` (and any future
 * code repo) without per-repo client instances.
 *
 * Uses [libs.kotlinx.KotlinxJson] for deserialization; snake_case fields are
 * mapped via explicit @SerialName annotations on the model classes.
 */
class GithubClient(
    private val config: GithubConfig,
    private val client: HttpHandler = JavaHttpClient(),
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
            val request = authRequest(Method.GET, url)
                .query("state", "all")
                .query("labels", labels.joinToString(","))
                .query("since", since.toString())
                .query("per_page", "100")
            execute(request).decodeJson()
        } catch (e: CancellationException) {
            throw e
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
            execute(authRequest(Method.GET, url)).decodeJson()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            appLog.warn("failed to fetch github commit repo=$repo sha=$sha", t)
            null
        }
    }

    suspend fun auditCommits(repo: String, branch: String, since: Instant): List<CapturedCommit> {
        val url = "${config.apiUrl}/repos/$repo/commits"
        return pagedJson(url) { req, page ->
            req.query("sha", branch)
                .query("since", since.toString())
                .query("page", page.toString())
        }.map { raw -> CapturedCommit(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw) }
    }

    suspend fun auditCommit(repo: String, sha: String): CapturedCommit {
        val raw = auditJson("${config.apiUrl}/repos/$repo/commits/$sha")
        return CapturedCommit(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw)
    }

    suspend fun auditWorkflowRuns(repo: String, workflowFile: String, since: Instant): List<CapturedWorkflowRun> {
        val url = "${config.apiUrl}/repos/$repo/actions/workflows/$workflowFile/runs"
        val pages = pagedJson(url, "workflow_runs") { req, page ->
            req.query("branch", "main")
                .query("created", ">=${since}")
                .query("page", page.toString())
        }
        return pages.map { raw -> CapturedWorkflowRun(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw) }
            .filter { it.value.event in DEPLOY_EVENTS }
    }

    suspend fun auditWorkflowJobs(repo: String, runId: Long, attempt: Int): CapturedWorkflowJobs {
        val url = "${config.apiUrl}/repos/$repo/actions/runs/$runId/attempts/$attempt/jobs"
        val raw = auditJson(url) { it.query("per_page", "100") }
        return CapturedWorkflowJobs(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw)
    }

    suspend fun auditWorkflowRunAttempt(repo: String, runId: Long, attempt: Int): CapturedWorkflowRun {
        val raw = auditJson("${config.apiUrl}/repos/$repo/actions/runs/$runId/attempts/$attempt")
        return CapturedWorkflowRun(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw)
    }

    suspend fun compareCommits(repo: String, base: String, head: String): List<CapturedCommit> {
        val commits = pagedJson("${config.apiUrl}/repos/$repo/compare/$base...$head", "commits") { req, page ->
            req.query("page", page.toString())
        }
        return commits.map { commit ->
            CapturedCommit(libs.kotlinx.KotlinxJson.decodeFromJsonElement(commit), commit)
        }
    }

    suspend fun branchProtection(repo: String, branch: String): CapturedControl = auditControl(
        "${config.apiUrl}/repos/$repo/branches/$branch/protection",
    )

    suspend fun repositoryRulesets(repo: String): CapturedControl {
        val list = auditControl("${config.apiUrl}/repos/$repo/rulesets") {
            it.query("includes_parents", "true")
        }
        if (list.status != FetchStatus.PRESENT) return list

        val details = buildJsonArray {
            list.payload.jsonArray.forEach { summary ->
                val url = summary.jsonObject["_links"]?.jsonObject
                    ?.get("self")?.jsonObject?.get("href")?.jsonPrimitive?.content
                    ?: return@forEach
                val configured = config.apiUrl.toURI()
                val candidate = URI(url)
                require(candidate.scheme == configured.scheme && candidate.host == configured.host && candidate.port == configured.port) {
                    "GitHub ruleset URL points outside configured API"
                }
                add(auditJson(url))
            }
        }
        return CapturedControl(
            FetchStatus.PRESENT,
            buildJsonObject {
                put("summaries", list.payload)
                put("details", details)
            },
        )
    }

    suspend fun workflowSource(repo: String, workflowPath: String, sha: String): WorkflowSource {
        val path = workflowPath.removePrefix("/")
        val raw = auditJson("${config.apiUrl}/repos/$repo/contents/$path") { it.query("ref", sha) }
        val value = raw.jsonObject
        val encoded = value["content"]?.jsonPrimitive?.content?.replace("\n", "")
            ?: error("GitHub contents response is missing content for repo=$repo path=$path sha=$sha")
        return WorkflowSource(
            path = value["path"]?.jsonPrimitive?.content ?: path,
            ref = sha,
            blobSha = value["sha"]?.jsonPrimitive?.content
                ?: error("GitHub contents response is missing sha for repo=$repo path=$path sha=$sha"),
            content = Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8),
            raw = raw,
        )
    }

    suspend fun issueComments(repo: String, issueNumber: Long): List<GithubIssueComment> {
        val url = "${config.apiUrl}/repos/$repo/issues/$issueNumber/comments"
        return pagedJson(url) { req, page -> req.query("page", page.toString()) }
            .map { libs.kotlinx.KotlinxJson.decodeFromJsonElement(it) }
    }

    suspend fun createIssueComment(repo: String, issueNumber: Long, body: String): GithubIssueComment {
        val request = authRequest(Method.POST, "${config.apiUrl}/repos/$repo/issues/$issueNumber/comments")
            .header("Content-Type", "application/json")
            .body(libs.kotlinx.KotlinxJson.encodeToString(IssueCommentBody(body)))
        val response = execute(request)
        if (!response.status.successful) throw GithubHttpException(response.status)
        return response.decodeJson()
    }

    suspend fun updateIssueComment(repo: String, commentId: Long, body: String): GithubIssueComment? {
        val request = authRequest(Method.PATCH, "${config.apiUrl}/repos/$repo/issues/comments/$commentId")
            .header("Content-Type", "application/json")
            .body(libs.kotlinx.KotlinxJson.encodeToString(IssueCommentBody(body)))
        val response = execute(request)
        return when {
            response.status == Status.NOT_FOUND -> null
            !response.status.successful -> throw GithubHttpException(response.status)
            else -> response.decodeJson()
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
            val request = authRequest(Method.GET, url)
                .query("branch", "main")
                .query("created", ">=${since}")
                .query("per_page", "100")
            val page: WorkflowRunsPage = execute(request).decodeJson()
            page.workflowRuns.filter { it.event in DEPLOY_EVENTS }
        } catch (e: CancellationException) {
            throw e
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
            val request = authRequest(Method.GET, url).query("per_page", "100")
            val page: WorkflowJobsPage = execute(request).decodeJson()
            page.jobs
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            appLog.warn("failed to list github workflow jobs repo=$repo runId=$runId", t)
            emptyList()
        }
    }

    private suspend fun authRequest(method: Method, url: String): Request =
        Request(method, url)
            .header("Authorization", "Bearer ${app.token()}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun execute(request: Request): Response =
        runInterruptible { client(request) }

    private suspend fun auditJson(
        url: String,
        build: (Request) -> Request = { it },
    ): JsonElement {
        val response = execute(build(authRequest(Method.GET, url)))
        if (!response.status.successful) throw GithubHttpException(response.status)
        return response.decodeJson()
    }

    private suspend fun auditControl(
        url: String,
        build: (Request) -> Request = { it },
    ): CapturedControl = try {
        CapturedControl(FetchStatus.PRESENT, auditJson(url, build))
    } catch (e: GithubHttpException) {
        when (e.status) {
            Status.NOT_FOUND -> CapturedControl(FetchStatus.NOT_FOUND, JsonNull)
            Status.FORBIDDEN, Status.UNAUTHORIZED -> CapturedControl(
                FetchStatus.FORBIDDEN,
                JsonPrimitive(e.status.code),
            )
            else -> CapturedControl(FetchStatus.FAILED, JsonPrimitive(e.status.code))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        appLog.warn("failed to fetch github audit control url=$url", t)
        CapturedControl(FetchStatus.FAILED, JsonNull)
    }

    private suspend fun pagedJson(
        url: String,
        arrayField: String? = null,
        build: (Request, Int) -> Request,
    ): List<JsonElement> {
        val result = mutableListOf<JsonElement>()
        var page = 1
        while (true) {
            val request = build(authRequest(Method.GET, url).query("per_page", "100"), page)
            val response = execute(request).decodeJson<JsonElement>()
            val entries = arrayField?.let { response.jsonObject[it]?.jsonArray } ?: response.jsonArray
            result.addAll(entries)
            if (entries.size < 100) break
            page++
        }
        return result
    }

    companion object {
        private val DEPLOY_EVENTS = setOf("push", "workflow_dispatch", "workflow_run")
    }

    @Serializable
    private data class IssueCommentBody(val body: String)
}

/** Non-2xx response from the GitHub REST API, used for status-code mapping. */
internal class GithubHttpException(val status: Status) :
    RuntimeException("GitHub returned HTTP ${status.code}")

private inline fun <reified T> Response.decodeJson(): T =
    libs.kotlinx.KotlinxJson.decodeFromString(bodyString())
