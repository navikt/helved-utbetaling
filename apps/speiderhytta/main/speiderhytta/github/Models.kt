package speiderhytta.github

import java.time.Instant

/**
 * Subset of GitHub Issues, Commits, and Actions API responses we care about.
 *
 * Unknown fields are ignored by the Jackson mapper configured in [GithubClient],
 * so the rich GitHub payload doesn't break us when fields are added. Snake_case
 * field names (e.g. `head_sha`) are mapped to camelCase via the same mapper.
 */
data class GithubIssue(
    val number: Long,
    val title: String,
    val state: String,
    val body: String?,
    val labels: List<GithubLabel> = emptyList(),
    val createdAt: Instant,
    val closedAt: Instant?,
)

data class GithubLabel(val name: String)

data class GithubCommit(
    val sha: String,
    val commit: CommitDetail,
)

data class CommitDetail(
    val author: CommitAuthor,
)

data class CommitAuthor(
    val date: Instant,
)

/**
 * One workflow run as returned by `GET /repos/{owner}/{repo}/actions/workflows/{file}/runs`.
 *
 * `conclusion` is null while the run is still in progress; we skip those and
 * pick them up on a later poll. Real values seen in helved: `success`,
 * `failure`, `cancelled` (no `failure` in the last 100 push runs at the time
 * of writing — most "failures" surface as labelled GitHub Issues instead).
 */
data class WorkflowRun(
    val id: Long,
    val name: String,
    val headSha: String,
    val headBranch: String?,
    val event: String,
    val status: String,
    val conclusion: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val htmlUrl: String,
)

/**
 * Wrapper for the paginated `actions/workflows/.../runs` endpoint.
 * GitHub returns `{ total_count, workflow_runs: [...] }` rather than a bare
 * array, unlike `/issues` and `/jobs`.
 */
data class WorkflowRunsPage(
    val totalCount: Long = 0,
    val workflowRuns: List<WorkflowRun> = emptyList(),
)

/**
 * A single job within a workflow run. We only consume the standard
 * `deploy-prod` job; other job names (`build`, `deploy-dev`, `check`) are
 * ignored. `startedAt`/`completedAt` are nullable per the GitHub schema —
 * absent when the job hasn't started or finished respectively.
 */
data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
)

/**
 * Wrapper for `GET /repos/{owner}/{repo}/actions/runs/{id}/jobs` which
 * returns `{ total_count, jobs: [...] }`.
 */
data class WorkflowJobsPage(
    val totalCount: Long = 0,
    val jobs: List<WorkflowJob> = emptyList(),
)
