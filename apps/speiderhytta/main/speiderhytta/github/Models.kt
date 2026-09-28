@file:UseSerializers(libs.kotlinx.InstantSerializer::class)

package speiderhytta.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Subset of GitHub Issues, Commits, and Actions API responses we care about.
 *
 * Unknown fields are ignored by [speiderhytta.speiderhyttaJson] (`ignoreUnknownKeys = true`).
 * Snake_case GitHub field names are mapped via explicit @SerialName annotations.
 */
@Serializable
data class GithubIssue(
    val number: Long,
    val title: String,
    val state: String,
    val body: String?,
    val labels: List<GithubLabel> = emptyList(),
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("closed_at") val closedAt: Instant?,
)

@Serializable
data class GithubLabel(val name: String)

@Serializable
data class GithubIssueComment(
    val id: Long,
    val body: String? = null,
    val user: GithubUser? = null,
)

@Serializable
data class GithubCommit(
    val sha: String,
    val commit: CommitDetail,
    val author: GithubUser? = null,
    val committer: GithubUser? = null,
    val parents: List<CommitParent> = emptyList(),
)

@Serializable
data class CommitDetail(
    val author: CommitAuthor,
    val committer: CommitAuthor = author,
    val message: String = "",
    val verification: CommitVerification? = null,
)

@Serializable
data class CommitAuthor(
    val date: Instant,
)

@Serializable
data class GithubUser(val login: String)

@Serializable
data class CommitVerification(
    val verified: Boolean,
    val reason: String? = null,
)

@Serializable
data class CommitParent(val sha: String)

/**
 * One workflow run as returned by `GET /repos/{owner}/{repo}/actions/workflows/{file}/runs`.
 *
 * `conclusion` is null while the run is still in progress; we skip those and
 * pick them up on a later poll. Real values seen in helved: `success`,
 * `failure`, `cancelled` (no `failure` in the last 100 push runs at the time
 * of writing — most "failures" surface as labelled GitHub Issues instead).
 */
@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String,
    @SerialName("head_sha") val headSha: String,
    @SerialName("head_branch") val headBranch: String?,
    val event: String,
    val status: String,
    val conclusion: String?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("run_attempt") val runAttempt: Int = 1,
    val path: String? = null,
    val actor: GithubUser? = null,
    @SerialName("triggering_actor") val triggeringActor: GithubUser? = null,
    @SerialName("run_started_at") val runStartedAt: Instant? = null,
)

/**
 * Wrapper for the paginated `actions/workflows/.../runs` endpoint.
 * GitHub returns `{ total_count, workflow_runs: [...] }` rather than a bare
 * array, unlike `/issues` and `/jobs`.
 */
@Serializable
data class WorkflowRunsPage(
    @SerialName("total_count") val totalCount: Long = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList(),
)

/**
 * A single job within a workflow run. We only consume the standard
 * `deploy-prod` job; other job names (`build`, `deploy-dev`, `check`) are
 * ignored. `startedAt`/`completedAt` are nullable per the GitHub schema —
 * absent when the job hasn't started or finished respectively.
 */
@Serializable
data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    @SerialName("started_at") val startedAt: Instant?,
    @SerialName("completed_at") val completedAt: Instant?,
    @SerialName("run_id") val runId: Long = 0,
    @SerialName("run_attempt") val runAttempt: Int = 1,
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("created_at") val createdAt: Instant = startedAt ?: Instant.EPOCH,
    val steps: List<WorkflowStep> = emptyList(),
)

@Serializable
data class WorkflowStep(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int,
    @SerialName("started_at") val startedAt: Instant? = null,
    @SerialName("completed_at") val completedAt: Instant? = null,
)

/**
 * Wrapper for `GET /repos/{owner}/{repo}/actions/runs/{id}/jobs` which
 * returns `{ total_count, jobs: [...] }`.
 */
@Serializable
data class WorkflowJobsPage(
    @SerialName("total_count") val totalCount: Long = 0,
    val jobs: List<WorkflowJob> = emptyList(),
)

data class CapturedCommit(val value: GithubCommit, val raw: JsonElement)

data class CapturedWorkflowRun(val value: WorkflowRun, val raw: JsonElement)

data class CapturedWorkflowJobs(val value: WorkflowJobsPage, val raw: JsonElement)

data class WorkflowSource(
    val path: String,
    val ref: String,
    val blobSha: String,
    val content: String,
    val raw: JsonElement,
)

enum class FetchStatus { PRESENT, NOT_FOUND, FORBIDDEN, FAILED }

data class CapturedControl(val status: FetchStatus, val payload: JsonElement)
