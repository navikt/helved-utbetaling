package speiderhytta.dora

import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import speiderhytta.CodeRepoConfig
import speiderhytta.Metrics
import speiderhytta.github.GithubClient
import speiderhytta.github.WorkflowJob
import speiderhytta.github.WorkflowRun
import java.time.Instant

/**
 * Source of GitHub Actions workflow runs and commit metadata, scoped to a
 * specific code repo. Tests pass an inline implementation so we don't need
 * a fake GitHub server.
 */
interface DeployFetcher {
    suspend fun runs(repo: String, workflowFile: String, since: Instant): List<WorkflowRun>
    suspend fun jobs(repo: String, runId: Long): List<WorkflowJob>
    /** Commit author timestamp, used as the lead-time anchor. Null on any failure. */
    suspend fun commitAuthorTime(repo: String, sha: String): Instant?
}

fun GithubClient.asDeployFetcher(): DeployFetcher = object : DeployFetcher {
    override suspend fun runs(repo: String, workflowFile: String, since: Instant) =
        appWorkflowRuns(repo, workflowFile, since)
    override suspend fun jobs(repo: String, runId: Long) = workflowRunJobs(repo, runId)
    override suspend fun commitAuthorTime(repo: String, sha: String): Instant? =
        commit(repo, sha)?.commit?.author?.date
}

/**
 * Pulls deploy attempts from GitHub Actions workflow runs and stores them in
 * `deployment`. NAIS does not expose a deployment-history API, so the
 * `deploy-prod` job within each app's workflow file is our canonical record.
 *
 * Iterates every (repo, app) pair: helved-utbetaling contributes 10 apps
 * (one workflow file each, `<app>.yml`); helved-peisen contributes the
 * `peisen` app via `deploy.yaml`.
 *
 * Lead time (commit → deploy) is only meaningful for successful deploys; for
 * `failure` or `cancelled` outcomes we store `0` and exclude those rows from
 * lead-time percentile calculations.
 *
 * Apps without a `deploy-prod` job (currently `snickerboa`) silently produce
 * no rows — that's the correct result, not an error.
 */
class DeployService(
    private val deploys: DeployFetcher,
    private val metrics: Metrics,
    private val codeRepos: List<CodeRepoConfig>,
    private val jdbcCtx: CoroutineDatasource,
    private val targetEnv: String = "prod-gcp",
    private val deployJobName: String = "deploy-prod",
) {
    /**
     * Process all deploy attempts newer than [since] and return the latest
     * `deploy_finished_ts` we observed (used to advance the poller cursor).
     */
    suspend fun ingest(since: Instant): Instant {
        var newCursor = since
        for (codeRepo in codeRepos) {
            for ((app, workflowFile) in codeRepo.apps) {
                val runs = deploys.runs(codeRepo.repo, workflowFile, since)
                for (run in runs) {
                    val deployment = toDeployment(codeRepo.repo, app, run) ?: continue
                    withContext(jdbcCtx) {
                        transaction {
                            val inserted = deployment.insert()
                            if (inserted > 0) {
                                appLog.info(
                                    "recorded deployment app={} sha={} outcome={} runId={}",
                                    deployment.app, deployment.sha, deployment.outcome, deployment.runId,
                                )
                                metrics.deployObserved()
                            }
                        }
                    }
                    if (deployment.deployFinishedTs.isAfter(newCursor)) {
                        newCursor = deployment.deployFinishedTs
                    }
                }
            }
        }
        return newCursor
    }

    private suspend fun toDeployment(repo: String, app: String, run: WorkflowRun): Deployment? {
        // Skip in-progress runs; we'll see them on a later poll once finished.
        if (run.conclusion == null) return null

        val deployJob = deploys.jobs(repo, run.id).firstOrNull { it.name == deployJobName } ?: return null
        val started = deployJob.startedAt ?: return null
        val finished = deployJob.completedAt ?: return null

        val outcome = when (deployJob.conclusion) {
            "success" -> "success"
            "failure", "timed_out" -> "failure"
            "cancelled", "skipped" -> "cancelled"
            else -> return null // neutral, action_required, stale, null — not a clear deploy attempt
        }

        // Lead time only makes sense for successes — a failed deploy never
        // shipped the commit, so its lead time is 0 by convention.
        val commitTs = if (outcome == "success") {
            deploys.commitAuthorTime(repo, run.headSha) ?: started
        } else {
            started
        }
        val leadSeconds = if (outcome == "success") {
            (finished.epochSecond - commitTs.epochSecond).coerceAtLeast(0)
        } else {
            0L
        }

        return Deployment(
            app = app,
            sha = run.headSha,
            env = targetEnv,
            commitTs = commitTs,
            deployStartedTs = started,
            deployFinishedTs = finished,
            leadTimeSeconds = leadSeconds,
            outcome = outcome,
            runId = run.id,
            runUrl = run.htmlUrl,
        )
    }
}
