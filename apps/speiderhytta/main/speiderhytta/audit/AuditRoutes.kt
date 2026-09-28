package speiderhytta.audit

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import java.time.Instant

fun Route.auditRoutes(
    jdbcCtx: CoroutineDatasource,
    codeRepositories: Set<String>,
    taskRepository: String,
) {
    route("/audit") {
        get("/tasks/{owner}/{repo}/{number}") {
            val repository = repository(call.parameters["owner"], call.parameters["repo"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, "missing repository")
            if (repository != taskRepository) return@get call.respond(HttpStatusCode.NotFound, "unknown task repository")
            val number = call.parameters["number"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "invalid task number")
            val limit = limit(call.request.queryParameters["limit"])
            val rows = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { AuditCommit.selectForTask(repository, number, limit) }
            }
            call.respond(rows)
        }

        get("/commits/{owner}/{repo}/{sha}") {
            val repository = repository(call.parameters["owner"], call.parameters["repo"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, "missing repository")
            if (repository !in codeRepositories) return@get call.respond(HttpStatusCode.NotFound, "unknown repository")
            val sha = call.parameters["sha"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing sha")
            val row = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { AuditCommit.find(repository, sha) }
            } ?: return@get call.respond(HttpStatusCode.NotFound, "unknown commit")
            call.respond(row)
        }

        get("/workflows/{owner}/{repo}/{runId}") {
            val repository = repository(call.parameters["owner"], call.parameters["repo"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, "missing repository")
            if (repository !in codeRepositories) return@get call.respond(HttpStatusCode.NotFound, "unknown repository")
            val runId = call.parameters["runId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "invalid run id")
            val row = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { AuditWorkflowExecution.find(repository, runId) }
            } ?: return@get call.respond(HttpStatusCode.NotFound, "unknown workflow run")
            call.respond(row)
        }

        get("/controls/{owner}/{repo}/{branch}") {
            val repository = repository(call.parameters["owner"], call.parameters["repo"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, "missing repository")
            if (repository !in codeRepositories) return@get call.respond(HttpStatusCode.NotFound, "unknown repository")
            val branch = call.parameters["branch"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing branch")
            val at = call.request.queryParameters["at"]?.let {
                runCatching { Instant.parse(it) }.getOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "invalid at timestamp")
            } ?: Instant.now()
            val rows = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { AuditControlSnapshot.at(repository, branch, at) }
            }
            call.respond(rows)
        }

        get("/evidence/{owner}/{repo}/workflows/{runId}") {
            val repository = repository(call.parameters["owner"], call.parameters["repo"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, "missing repository")
            if (repository !in codeRepositories) return@get call.respond(HttpStatusCode.NotFound, "unknown repository")
            val runId = call.parameters["runId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "invalid run id")
            val evidence = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { evidence(repository, runId) }
            } ?: return@get call.respond(HttpStatusCode.NotFound, "unknown workflow run")
            call.respond(evidence)
        }
    }
}

@Serializable
data class AuditEvidence(
    val attempts: List<AuditAttemptEvidence>,
    val commits: List<AuditCommit>,
    val controls: List<AuditControlSnapshot>,
)

@Serializable
data class AuditAttemptEvidence(
    val workflow: AuditWorkflowExecution,
    val source: AuditWorkflowSource?,
    val jobs: List<AuditJobEvidence>,
)

@Serializable
data class AuditJobEvidence(val job: AuditWorkflowJob, val steps: List<AuditWorkflowStep>)

internal suspend fun evidence(repository: String, runId: Long): AuditEvidence? {
    val workflows = AuditWorkflowExecution.attempts(repository, runId)
    if (workflows.isEmpty()) return null
    val attempts = workflows.map { workflow ->
        val workflowId = workflow.id ?: error("audit workflow id is missing")
        val jobs = AuditWorkflowJob.forExecution(workflowId).map { job ->
            AuditJobEvidence(job, AuditWorkflowStep.forJob(job.id ?: error("audit job id is missing")))
        }
        AuditAttemptEvidence(workflow, AuditWorkflowSource.find(workflowId), jobs)
    }
    val latest = workflows.maxBy { it.runAttempt }
    val deploymentAttempt = attempts.lastOrNull { attempt ->
        attempt.jobs.any { it.job.name == "deploy-prod" && it.job.conclusion == "success" }
    }
    val evidenceWorkflow = deploymentAttempt?.workflow ?: latest
    val evidenceWorkflowId = evidenceWorkflow.id ?: error("audit workflow id is missing")
    return AuditEvidence(
        attempts = attempts,
        commits = AuditWorkflowExecutionCommit.commitsForExecution(evidenceWorkflowId),
        controls = AuditControlSnapshot.at(repository, "main", evidenceWorkflow.updatedAt),
    )
}

private fun repository(owner: String?, repo: String?): String? =
    if (owner.isNullOrBlank() || repo.isNullOrBlank()) null else "$owner/$repo"

private fun limit(raw: String?): Int = raw?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
