package speiderhytta.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import libs.http.HttpClientFactory
import libs.utils.appLog
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
 * Uses [speiderhyttaJson] for deserialization; snake_case fields are mapped
 * via explicit @SerialName annotations on the model classes.
 */
class GithubClient(
    private val config: GithubConfig,
    private val client: HttpClient = HttpClientFactory.new(libs.kotlinx.KotlinxJson, LogLevel.INFO),
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

    suspend fun auditCommits(repo: String, branch: String, since: Instant): List<CapturedCommit> {
        val url = "${config.apiUrl}/repos/$repo/commits"
        return pagedJson(url) { page ->
            parameter("sha", branch)
            parameter("since", since.toString())
            parameter("page", page)
        }.map { raw -> CapturedCommit(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw) }
    }

    suspend fun auditCommit(repo: String, sha: String): CapturedCommit {
        val raw = auditJson("${config.apiUrl}/repos/$repo/commits/$sha")
        return CapturedCommit(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw)
    }

    suspend fun auditWorkflowRuns(repo: String, workflowFile: String, since: Instant): List<CapturedWorkflowRun> {
        val url = "${config.apiUrl}/repos/$repo/actions/workflows/$workflowFile/runs"
        val pages = pagedJson(url, "workflow_runs") { page ->
            parameter("branch", "main")
            parameter("created", ">=${since}")
            parameter("page", page)
        }
        return pages.map { raw -> CapturedWorkflowRun(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw) }
            .filter { it.value.event in DEPLOY_EVENTS }
    }

    suspend fun auditWorkflowJobs(repo: String, runId: Long, attempt: Int): CapturedWorkflowJobs {
        val url = "${config.apiUrl}/repos/$repo/actions/runs/$runId/attempts/$attempt/jobs"
        val raw = auditJson(url) { parameter("per_page", 100) }
        return CapturedWorkflowJobs(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw)
    }

    suspend fun auditWorkflowRunAttempt(repo: String, runId: Long, attempt: Int): CapturedWorkflowRun {
        val raw = auditJson("${config.apiUrl}/repos/$repo/actions/runs/$runId/attempts/$attempt")
        return CapturedWorkflowRun(libs.kotlinx.KotlinxJson.decodeFromJsonElement(raw), raw)
    }

    suspend fun compareCommits(repo: String, base: String, head: String): List<CapturedCommit> {
        val commits = pagedJson("${config.apiUrl}/repos/$repo/compare/$base...$head", "commits") { page ->
            parameter("page", page)
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
            parameter("includes_parents", true)
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
        val raw = auditJson("${config.apiUrl}/repos/$repo/contents/$path") { parameter("ref", sha) }
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
        return pagedJson(url) { page -> parameter("page", page) }
            .map { libs.kotlinx.KotlinxJson.decodeFromJsonElement(it) }
    }

    suspend fun createIssueComment(repo: String, issueNumber: Long, body: String): GithubIssueComment = client.post(
        "${config.apiUrl}/repos/$repo/issues/$issueNumber/comments",
    ) {
        expectSuccess = true
        bearerAuth(app.token())
        acceptJson()
        contentType(ContentType.Application.Json)
        setBody(IssueCommentBody(body))
    }.body()

    suspend fun updateIssueComment(repo: String, commentId: Long, body: String): GithubIssueComment? = try {
        client.patch("${config.apiUrl}/repos/$repo/issues/comments/$commentId") {
            expectSuccess = true
            bearerAuth(app.token())
            acceptJson()
            contentType(ContentType.Application.Json)
            setBody(IssueCommentBody(body))
        }.body()
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
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

    private suspend fun auditJson(
        url: String,
        parameters: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): JsonElement = client.get(url) {
        expectSuccess = true
        bearerAuth(app.token())
        acceptJson()
        parameters()
    }.body()

    private suspend fun auditControl(
        url: String,
        parameters: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): CapturedControl = try {
        CapturedControl(FetchStatus.PRESENT, auditJson(url, parameters))
    } catch (e: ClientRequestException) {
        when (e.response.status) {
            HttpStatusCode.NotFound -> CapturedControl(FetchStatus.NOT_FOUND, JsonNull)
            HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized -> CapturedControl(
                FetchStatus.FORBIDDEN,
                JsonPrimitive(e.response.status.value),
            )
            else -> CapturedControl(FetchStatus.FAILED, JsonPrimitive(e.response.status.value))
        }
    } catch (e: ResponseException) {
        CapturedControl(FetchStatus.FAILED, JsonPrimitive(e.response.status.value))
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        appLog.warn("failed to fetch github audit control url=$url", t)
        CapturedControl(FetchStatus.FAILED, JsonNull)
    }

    private suspend fun pagedJson(
        url: String,
        arrayField: String? = null,
        parameters: io.ktor.client.request.HttpRequestBuilder.(Int) -> Unit,
    ): List<JsonElement> {
        val result = mutableListOf<JsonElement>()
        var page = 1
        while (true) {
            val response = client.get(url) {
                bearerAuth(app.token())
                acceptJson()
                parameter("per_page", 100)
                parameters(page)
            }.body<JsonElement>()   
            val entries = arrayField?.let { response.jsonObject[it]?.jsonArray } ?: response.jsonArray
            result.addAll(entries)
            if (entries.size < 100) break
            page++
        }
        return result
    }

    private fun io.ktor.client.request.HttpRequestBuilder.acceptJson() {
        headers {
            append(HttpHeaders.Accept, "application/vnd.github+json")
            append("X-GitHub-Api-Version", "2022-11-28")
        }
    }

    companion object {
        private val DEPLOY_EVENTS = setOf("push", "workflow_dispatch", "workflow_run")
    }

    @Serializable
    private data class IssueCommentBody(val body: String)
}
