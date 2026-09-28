package speiderhytta.audit

import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import speiderhytta.CodeRepoConfig
import speiderhytta.github.CapturedCommit
import speiderhytta.github.CapturedControl
import speiderhytta.github.CapturedWorkflowJobs
import speiderhytta.github.CapturedWorkflowRun
import speiderhytta.github.FetchStatus
import speiderhytta.github.GithubClient
import speiderhytta.github.WorkflowSource
import java.security.MessageDigest
import java.time.Instant

interface AuditFetcher {
    suspend fun commits(repo: String, since: Instant): List<CapturedCommit>
    suspend fun commit(repo: String, sha: String): CapturedCommit
    suspend fun runs(repo: String, workflowFile: String, since: Instant): List<CapturedWorkflowRun>
    suspend fun runAttempt(repo: String, runId: Long, attempt: Int): CapturedWorkflowRun
    suspend fun compareCommits(repo: String, base: String, head: String): List<CapturedCommit>
    suspend fun jobs(repo: String, runId: Long, attempt: Int): CapturedWorkflowJobs
    suspend fun workflowSource(repo: String, workflowPath: String, sha: String): WorkflowSource
    suspend fun branchProtection(repo: String, branch: String): CapturedControl
    suspend fun rulesets(repo: String): CapturedControl
}

fun GithubClient.asAuditFetcher(): AuditFetcher = object : AuditFetcher {
    override suspend fun commits(repo: String, since: Instant) = auditCommits(repo, "main", since)
    override suspend fun commit(repo: String, sha: String) = auditCommit(repo, sha)
    override suspend fun runs(repo: String, workflowFile: String, since: Instant) = auditWorkflowRuns(repo, workflowFile, since)
    override suspend fun runAttempt(repo: String, runId: Long, attempt: Int) = auditWorkflowRunAttempt(repo, runId, attempt)
    override suspend fun compareCommits(repo: String, base: String, head: String) =
        this@asAuditFetcher.compareCommits(repo, base, head)
    override suspend fun jobs(repo: String, runId: Long, attempt: Int) = auditWorkflowJobs(repo, runId, attempt)
    override suspend fun workflowSource(repo: String, workflowPath: String, sha: String) =
        this@asAuditFetcher.workflowSource(repo, workflowPath, sha)
    override suspend fun branchProtection(repo: String, branch: String) = this@asAuditFetcher.branchProtection(repo, branch)
    override suspend fun rulesets(repo: String) = repositoryRulesets(repo)
}

class AuditService(
    private val fetcher: AuditFetcher,
    private val codeRepos: List<CodeRepoConfig>,
    private val jdbcCtx: CoroutineDatasource,
    private val taskRepository: String,
    private val taskComments: TaskCommentProjector? = null,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun ingestCommits(repository: String, since: Instant): Instant {
        val commits = fetcher.commits(repository, since)
        storeCommits(repository, commits)
        taskComments?.update(commits.mapNotNull { it.taskReference() }.toSet())
        return commits.maxOfOrNull { it.value.commit.committer.date }
            ?.takeIf { it.isAfter(since) }
            ?: since
    }

    suspend fun ingestWorkflow(repository: String, app: String, workflowFile: String, since: Instant): Instant {
        val observedAt = now()
        val runs = fetcher.runs(repository, workflowFile, since).sortedBy { it.value.createdAt }
        runs.filter { it.value.conclusion != null }.forEach { captured ->
            storeRun(repository, app, workflowFile, captured)
        }
        val oldestUnfinished = runs.firstOrNull { it.value.conclusion == null }?.value?.createdAt
        val cursor = oldestUnfinished ?: observedAt
        return cursor.takeIf { it.isAfter(since) } ?: since
    }

    suspend fun snapshotControls(branch: String = "main") {
        codeRepos.map { it.repo }.distinct().forEach { repo ->
            storeControl(repo, branch, "branch_protection", fetcher.branchProtection(repo, branch))
            storeControl(repo, branch, "repository_rulesets", fetcher.rulesets(repo))
        }
    }

    private suspend fun storeCommits(repository: String, commits: List<CapturedCommit>) = withContext(jdbcCtx) {
        transaction {
            commits.forEach { captured -> captured.toAuditCommit(repository).insert() }
        }
    }

    private suspend fun storeRun(
        repository: String,
        app: String,
        workflowFile: String,
        captured: CapturedWorkflowRun,
    ) {
        val run = captured.value
        val attempts = (1..run.runAttempt).map { attempt ->
            val attemptRun = if (attempt == run.runAttempt) captured else fetcher.runAttempt(repository, run.id, attempt)
            attemptRun to fetcher.jobs(repository, run.id, attempt)
        }
        val path = run.path ?: ".github/workflows/$workflowFile"
        val source = fetcher.workflowSource(repository, path, run.headSha)
        val deploymentCommits = deploymentCommits(repository, app, run, attempts)

        withContext(jdbcCtx) {
            transaction {
                storeCommitsInCurrentTransaction(repository, deploymentCommits)
                attempts.forEach { (attemptRun, jobs) ->
                    val auditRun = attemptRun.value.toAuditRun(repository, app, workflowFile, attemptRun.raw, attemptRun.value.runAttempt)
                    auditRun.insert()
                    val storedExecution = AuditWorkflowExecution.find(repository, run.id, auditRun.runAttempt)
                        ?: error("audit workflow execution was not stored")
                    storeSource(storedExecution.id ?: error("audit workflow execution id is missing"), source)
                    storeJobs(storedExecution.id, jobs)
                    val deployedToProd = jobs.value.jobs.any { it.name == "deploy-prod" && it.conclusion == "success" }
                    if (deployedToProd) linkDeploymentCommits(storedExecution, deploymentCommits)
                }
            }
        }
    }

    private suspend fun storeSource(workflowExecutionId: Long, source: WorkflowSource) {
        AuditWorkflowSource(
            workflowExecutionId = workflowExecutionId,
            path = source.path,
            sourceRef = source.ref,
            blobSha = source.blobSha,
            content = source.content,
            contentSha256 = sha256(source.content),
            rawMetadata = source.raw,
        ).insert()
    }

    private suspend fun storeJobs(workflowExecutionId: Long, captured: CapturedWorkflowJobs) {
        captured.value.jobs.forEach { job ->
            val rawJob = captured.raw.jsonObject["jobs"]?.jsonArray
                ?.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content?.toLongOrNull() == job.id }
                ?: json.encodeToJsonElement(job)
            AuditWorkflowJob(
                workflowExecutionId = workflowExecutionId,
                githubJobId = job.id,
                name = job.name,
                status = job.status,
                conclusion = job.conclusion,
                createdAt = job.createdAt,
                startedAt = job.startedAt,
                completedAt = job.completedAt,
                rawMetadata = rawJob,
            ).insert()
            val storedJob = AuditWorkflowJob.find(workflowExecutionId, job.id) ?: error("audit job was not stored")
            job.steps.forEach { step ->
                AuditWorkflowStep(
                    workflowJobId = storedJob.id ?: error("audit job id is missing"),
                    number = step.number,
                    name = step.name,
                    status = step.status,
                    conclusion = step.conclusion,
                    startedAt = step.startedAt,
                    completedAt = step.completedAt,
                    rawMetadata = json.encodeToJsonElement(step),
                ).insert()
            }
        }
    }

    private suspend fun deploymentCommits(
        repository: String,
        app: String,
        run: speiderhytta.github.WorkflowRun,
        attempts: List<Pair<CapturedWorkflowRun, CapturedWorkflowJobs>>,
    ): List<CapturedCommit> {
        val deployedToProd = attempts.any { (_, jobs) ->
            jobs.value.jobs.any { it.name == "deploy-prod" && it.conclusion == "success" }
        }
        if (!deployedToProd) return emptyList()
        val previousHead = withContext(jdbcCtx) {
            transaction { AuditWorkflowExecution.previousSuccessfulDeploymentHead(repository, app, run.updatedAt) }
        }
        if (previousHead == null || previousHead == run.headSha) {
            return listOf(fetcher.commit(repository, run.headSha))
        }
        return fetcher.compareCommits(repository, previousHead, run.headSha)
    }

    private suspend fun linkDeploymentCommits(run: AuditWorkflowExecution, commits: List<CapturedCommit>) {
        val runId = run.id ?: return
        commits.mapNotNull { AuditCommit.find(run.repository, it.value.sha) }.distinctBy { it.sha }.forEach { commit ->
            AuditWorkflowExecutionCommit(runId, commit.id ?: error("audit commit id is missing")).insert()
        }
    }

    private suspend fun storeControl(repository: String, branch: String, type: String, control: CapturedControl) {
        val payloadHash = sha256("${control.status.name}:${canonical(control.payload)}")
        withContext(jdbcCtx) {
            transaction {
                val previous = AuditControlSnapshot.latest(repository, branch, type)
                if (previous?.payloadSha256 == payloadHash) return@transaction
                val capturedAt = Instant.now()
                AuditControlSnapshot(
                    repository = repository,
                    branch = branch,
                    controlType = type,
                    fetchStatus = control.status,
                    payload = control.payload,
                    payloadSha256 = payloadHash,
                    capturedAt = capturedAt,
                ).insert()
            }
        }
    }

    private fun CapturedCommit.toAuditCommit(repository: String): AuditCommit {
        val commit = value
        val task = taskReference()
        return AuditCommit(
            repository = repository,
            sha = commit.sha,
            taskRepository = task?.repository,
            taskNumber = task?.number,
            message = commit.commit.message,
            authorLogin = commit.author?.login,
            committerLogin = commit.committer?.login,
            authoredAt = commit.commit.author.date,
            committedAt = commit.commit.committer.date,
            signatureVerified = commit.commit.verification?.verified,
            verificationReason = commit.commit.verification?.reason,
            parents = json.encodeToJsonElement(commit.parents),
            rawMetadata = raw,
        )
    }

    private fun CapturedCommit.taskReference() = taskReference(value.commit.message, taskRepository)

    private suspend fun storeCommitsInCurrentTransaction(repository: String, commits: List<CapturedCommit>) {
        commits.forEach { it.toAuditCommit(repository).insert() }
    }
}

private fun speiderhytta.github.WorkflowRun.toAuditRun(
    repository: String,
    app: String,
    workflowFile: String,
    raw: JsonElement,
    attempt: Int?,
) = AuditWorkflowExecution(
    repository = repository,
    app = app,
    workflowFile = workflowFile,
    workflowPath = path,
    runId = id,
    runAttempt = attempt ?: runAttempt,
    headSha = headSha,
    event = event,
    status = status,
    conclusion = conclusion,
    actorLogin = actor?.login,
    triggeringActorLogin = triggeringActor?.login,
    createdAt = createdAt,
    runStartedAt = runStartedAt,
    updatedAt = updatedAt,
    runUrl = htmlUrl,
    rawMetadata = raw,
)

private fun canonical(value: JsonElement): String = when (value) {
    is JsonObject -> value.entries.sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, entry) -> "${JsonPrimitive(key)}:${canonical(entry)}" }
    is JsonArray -> value.joinToString(prefix = "[", postfix = "]") { canonical(it) }
    else -> value.toString()
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
