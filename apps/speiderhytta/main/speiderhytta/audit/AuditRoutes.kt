@file:UseSerializers(libs.kotlinx.InstantSerializer::class)

package speiderhytta.audit

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import libs.jdbc.Dao
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

fun Route.auditRoutes(
    jdbcCtx: CoroutineDatasource,
    codeRepositories: Set<String>,
) {
    route("/audit") {
        get("/workflows/{owner}/{repo}") {
            val repository = repository(call.parameters["owner"], call.parameters["repo"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, "missing repository")
            if (repository !in codeRepositories) return@get call.respond(HttpStatusCode.NotFound, "unknown repository")
            val from = instant(call.request.queryParameters["from"])
                ?: call.request.queryParameters["from"]?.let {
                    return@get call.respond(HttpStatusCode.BadRequest, "invalid from timestamp")
                }
            val to = instant(call.request.queryParameters["to"])
                ?: call.request.queryParameters["to"]?.let {
                    return@get call.respond(HttpStatusCode.BadRequest, "invalid to timestamp")
                }
            if (from != null && to != null && from.isAfter(to)) {
                return@get call.respond(HttpStatusCode.BadRequest, "from must be before or equal to to")
            }
            val app = call.request.queryParameters["app"]?.takeUnless { it.isBlank() }
            val limit = limit(call.request.queryParameters["limit"])
            val rows = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { WorkflowRunSummary.list(repository, app, from, to, limit) }
            }
            call.respond(rows)
        }

        get("/report/{owner}/{repo}/workflows/{runId}") {
            val repository = repository(call.parameters["owner"], call.parameters["repo"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, "missing repository")
            if (repository !in codeRepositories) return@get call.respond(HttpStatusCode.NotFound, "unknown repository")
            val runId = call.parameters["runId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "invalid run id")
            val report = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { report(repository, runId) }
            } ?: return@get call.respond(HttpStatusCode.NotFound, "unknown workflow run")
            call.respond(report)
        }
    }
}

@Serializable
data class AuditReport(
    val attempts: List<AuditAttemptReport>,
    val commits: List<AuditCommit>,
    val controls: List<AuditControlSnapshot>,
)

@Serializable
data class AuditAttemptReport(
    val workflow: AuditWorkflowExecution,
    val source: AuditWorkflowSource?,
    val jobs: List<AuditJobReport>,
)

@Serializable
data class AuditJobReport(val job: AuditWorkflowJob, val steps: List<AuditWorkflowStep>)

@Serializable
data class WorkflowRunSummary(
    val repository: String,
    val app: String,
    val workflowFile: String,
    val runId: Long,
    val headSha: String,
    val attemptCount: Int,
    val latestAttempt: Int,
    val status: String,
    val conclusion: String?,
    val deployProdConclusion: String?,
    val hasPreviousFailures: Boolean,
    val commitMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val runUrl: String,
) {
    companion object : Dao<WorkflowRunSummary> {
        override val table = "audit_workflow_execution"

        override fun from(rs: ResultSet) = WorkflowRunSummary(
            repository = rs.getString("repository"),
            app = rs.getString("app"),
            workflowFile = rs.getString("workflow_file"),
            runId = rs.getLong("run_id"),
            headSha = rs.getString("head_sha"),
            attemptCount = rs.getInt("attempt_count"),
            latestAttempt = rs.getInt("run_attempt"),
            status = rs.getString("status"),
            conclusion = rs.getString("conclusion"),
            deployProdConclusion = rs.getString("deploy_prod_conclusion"),
            hasPreviousFailures = rs.getBoolean("has_previous_failures"),
            commitMessage = rs.getString("commit_message"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            runUrl = rs.getString("run_url"),
        )

        suspend fun list(
            repository: String,
            app: String?,
            from: Instant?,
            to: Instant?,
            limit: Int,
        ): List<WorkflowRunSummary> {
            val filters = buildList {
                add("latest.repository = ?")
                if (app != null) add("latest.app = ?")
                if (from != null) add("latest.updated_at >= ?")
                if (to != null) add("latest.updated_at <= ?")
            }.joinToString(" AND ")
            return query(
                """
                WITH latest AS (
                    SELECT DISTINCT ON (repository, run_id) *
                    FROM $table
                    ORDER BY repository, run_id, run_attempt DESC
                )
                SELECT
                    latest.*,
                    (SELECT COUNT(*) FROM $table attempts
                     WHERE attempts.repository = latest.repository AND attempts.run_id = latest.run_id) AS attempt_count,
                    (SELECT jobs.conclusion FROM audit_workflow_job jobs
                     WHERE jobs.workflow_execution_id = latest.id AND jobs.name = 'deploy-prod'
                     ORDER BY jobs.github_job_id DESC LIMIT 1) AS deploy_prod_conclusion,
                    COALESCE((SELECT BOOL_OR(previous.conclusion IN ('failure', 'timed_out'))
                     FROM $table previous
                     WHERE previous.repository = latest.repository
                       AND previous.run_id = latest.run_id
                       AND previous.run_attempt < latest.run_attempt), FALSE) AS has_previous_failures,
                    (SELECT commits.message
                     FROM audit_workflow_execution_commit link
                     JOIN audit_commit commits ON commits.id = link.commit_id
                     JOIN $table executions ON executions.id = link.workflow_execution_id
                     WHERE executions.repository = latest.repository AND executions.run_id = latest.run_id
                     ORDER BY commits.committed_at DESC LIMIT 1) AS commit_message
                FROM latest
                WHERE $filters
                ORDER BY latest.updated_at DESC
                LIMIT ?
                """.trimIndent(),
            ) { stmt ->
                var index = 1
                stmt.setString(index++, repository)
                app?.let { stmt.setString(index++, it) }
                from?.let { stmt.setTimestamp(index++, Timestamp.from(it)) }
                to?.let { stmt.setTimestamp(index++, Timestamp.from(it)) }
                stmt.setInt(index, limit)
            }
        }
    }
}

internal suspend fun report(repository: String, runId: Long): AuditReport? {
    val workflows = AuditWorkflowExecution.attempts(repository, runId)
    if (workflows.isEmpty()) return null
    val attempts = workflows.map { workflow ->
        val workflowId = workflow.id ?: error("audit workflow id is missing")
        val jobs = AuditWorkflowJob.forExecution(workflowId).map { job ->
            AuditJobReport(job, AuditWorkflowStep.forJob(job.id ?: error("audit job id is missing")))
        }
        AuditAttemptReport(workflow, AuditWorkflowSource.find(workflowId), jobs)
    }
    val latest = workflows.maxBy { it.runAttempt }
    val deploymentAttempt = attempts.lastOrNull { attempt ->
        attempt.jobs.any { it.job.name == "deploy-prod" && it.job.conclusion == "success" }
    }
    val reportWorkflow = deploymentAttempt?.workflow ?: latest
    val reportWorkflowId = reportWorkflow.id ?: error("audit workflow id is missing")
    return AuditReport(
        attempts = attempts,
        commits = AuditWorkflowExecutionCommit.commitsForExecution(reportWorkflowId),
        controls = AuditControlSnapshot.at(repository, "main", reportWorkflow.updatedAt),
    )
}

private fun repository(owner: String?, repo: String?): String? =
    if (owner.isNullOrBlank() || repo.isNullOrBlank()) null else "$owner/$repo"

private fun limit(raw: String?): Int = raw?.toIntOrNull()?.coerceIn(1, 1000) ?: 100

private fun instant(raw: String?): Instant? = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
