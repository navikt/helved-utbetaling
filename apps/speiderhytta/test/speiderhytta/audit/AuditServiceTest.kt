package speiderhytta.audit

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.encodeToJsonElement
import libs.jdbc.concurrency.transaction
import speiderhytta.CodeRepoConfig
import speiderhytta.TestRuntime
import speiderhytta.github.CommitAuthor
import speiderhytta.github.CommitDetail
import speiderhytta.github.CommitVerification
import speiderhytta.github.CapturedCommit
import speiderhytta.github.CapturedWorkflowRun
import speiderhytta.github.CapturedWorkflowJobs
import speiderhytta.github.CapturedControl
import speiderhytta.github.FetchStatus
import speiderhytta.github.GithubCommit
import speiderhytta.github.GithubUser
import speiderhytta.github.WorkflowJob
import speiderhytta.github.WorkflowJobsPage
import speiderhytta.github.WorkflowRun
import speiderhytta.github.WorkflowRunsPage
import speiderhytta.github.WorkflowSource
import speiderhytta.github.WorkflowStep
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuditServiceTest {
    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `lagrer commit med task-kobling og komplett workflow-run`() = runTest(TestRuntime.context) {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        val service = AuditService(
            fetcher = FakeAuditFetcher(now),
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", mapOf("utsjekk" to "utsjekk.yml"))),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
        )

        assertEquals(now.plusSeconds(1), service.ingestCommits("navikt/helved-utbetaling", now.minusSeconds(3600)))
        service.ingestWorkflow("navikt/helved-utbetaling", "utsjekk", "utsjekk.yml", now.minusSeconds(3600))

        val commit = transaction { AuditCommit.find("navikt/helved-utbetaling", "abc123") }
        assertNotNull(commit)
        assertEquals("navikt/team-helved", commit.taskRepository)
        assertEquals(42L, commit.taskNumber)
        assertEquals(true, commit.signatureVerified)
        assertEquals("bot", commit.committerLogin)
        assertEquals(now.plusSeconds(1), commit.committedAt)

        val run = transaction { AuditWorkflowExecution.find("navikt/helved-utbetaling", 99) }
        assertNotNull(run)
        assertEquals(2, run.runAttempt)
        assertEquals("dev", run.actorLogin)
        val jobs = transaction { AuditWorkflowJob.query("SELECT * FROM audit_workflow_job") }
        assertEquals(listOf("deploy-prod", "test"), jobs.map { it.name }.sorted())
        val steps = transaction { AuditWorkflowStep.query("SELECT * FROM audit_workflow_step") }
        assertEquals(listOf("deploy", "gradle test"), steps.map { it.name }.sorted())
        val definitions = transaction { AuditWorkflowSource.query("SELECT * FROM audit_workflow_source") }
        assertEquals(2, definitions.size)
        assertEquals(setOf("name: utsjekk"), definitions.map { it.content }.toSet())
        val linkedCommits = transaction { AuditWorkflowExecutionCommit.forExecution(run.id!!) }
        assertEquals(1, linkedCommits.size)
    }

    @Test
    fun `lagrer bare kontrollendringer`() = runTest(TestRuntime.context) {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        val fetcher = FakeAuditFetcher(now)
        val service = AuditService(
            fetcher = fetcher,
            codeRepos = listOf(
                CodeRepoConfig("navikt/helved-utbetaling", emptyMap()),
                CodeRepoConfig("navikt/helved-utbetaling", emptyMap()),
            ),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
        )

        service.snapshotControls()
        fetcher.protection = JsonObject(
            linkedMapOf(
                "required_reviews" to JsonPrimitive(1),
                "enforce_admins" to JsonPrimitive(true),
            ),
        )
        service.snapshotControls()
        fetcher.protection = JsonObject(
            linkedMapOf(
                "enforce_admins" to JsonPrimitive(true),
                "required_reviews" to JsonPrimitive(1),
            ),
        )
        service.snapshotControls()
        fetcher.protectionStatus = FetchStatus.FORBIDDEN
        service.snapshotControls()

        val rows = transaction {
            AuditControlSnapshot.query(
                "SELECT * FROM audit_control_snapshot WHERE control_type = 'branch_protection' ORDER BY captured_at",
            )
        }
        assertEquals(3, rows.size)
        assertTrue(rows.first().payloadSha256 != rows.last().payloadSha256)
        assertEquals(FetchStatus.FORBIDDEN, rows.last().fetchStatus)
        assertEquals(4, fetcher.branchProtectionCalls)
        assertEquals(4, fetcher.rulesetCalls)
    }

    @Test
    fun `kobler commit-range mellom etterfølgende prod-deployer`() = runTest(TestRuntime.context) {
        val fetcher = DeploymentRangeAuditFetcher()
        val service = AuditService(
            fetcher = fetcher,
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", mapOf("utsjekk" to "utsjekk.yml"))),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
        )

        service.ingestWorkflow("navikt/helved-utbetaling", "utsjekk", "utsjekk.yml", Instant.EPOCH)
        fetcher.currentRun = 2
        service.ingestWorkflow("navikt/helved-utbetaling", "utsjekk", "utsjekk.yml", Instant.EPOCH)

        val first = transaction { AuditWorkflowExecution.find("navikt/helved-utbetaling", 1) }
        val second = transaction { AuditWorkflowExecution.find("navikt/helved-utbetaling", 2) }
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(listOf("aaa"), transaction { AuditWorkflowExecutionCommit.commitsForExecution(first.id!!) }.map { it.sha })
        assertEquals(listOf("bbb", "ccc"), transaction { AuditWorkflowExecutionCommit.commitsForExecution(second.id!!) }.map { it.sha })
        assertEquals(listOf("aaa" to "ccc"), fetcher.comparisons)
    }

    @Test
    fun `kobler ikke commits når prod-deploy feiler`() = runTest(TestRuntime.context) {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        val fetcher = FakeAuditFetcher(now).apply { deployConclusion = "failure" }
        val service = AuditService(
            fetcher = fetcher,
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", mapOf("utsjekk" to "utsjekk.yml"))),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
        )

        service.ingestWorkflow("navikt/helved-utbetaling", "utsjekk", "utsjekk.yml", now.minusSeconds(3600))

        val run = transaction { AuditWorkflowExecution.find("navikt/helved-utbetaling", 99, 2) }
        assertNotNull(run)
        assertEquals(emptyList(), transaction { AuditWorkflowExecutionCommit.forExecution(run.id!!) })
    }

    @Test
    fun `holder workflow-cursor ved uferdig run til den er fullført`() = runTest(TestRuntime.context) {
        val createdAt = Instant.parse("2026-09-21T12:00:00Z")
        val fetcher = LongRunningWorkflowFetcher(createdAt)
        val service = AuditService(
            fetcher = fetcher,
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", mapOf("utsjekk" to "utsjekk.yml"))),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
            now = { createdAt.plusSeconds(7200) },
        )

        val cursor = service.ingestWorkflow(
            "navikt/helved-utbetaling",
            "utsjekk",
            "utsjekk.yml",
            createdAt.minusSeconds(3600),
        )
        assertEquals(createdAt, cursor)
        assertEquals(null, transaction { AuditWorkflowExecution.find("navikt/helved-utbetaling", 77) })

        fetcher.completed = true
        val nextCursor = service.ingestWorkflow(
            "navikt/helved-utbetaling",
            "utsjekk",
            "utsjekk.yml",
            cursor.minusSeconds(600),
        )

        assertEquals(createdAt.plusSeconds(7200), nextCursor)
        assertNotNull(transaction { AuditWorkflowExecution.find("navikt/helved-utbetaling", 77) })
        assertEquals(listOf(createdAt.minusSeconds(3600), createdAt.minusSeconds(600)), fetcher.requestedSince)
    }

    @Test
    fun `samme ingest er idempotent for hele auditkjeden`() = runTest(TestRuntime.context) {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        val service = AuditService(
            fetcher = FakeAuditFetcher(now),
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", mapOf("utsjekk" to "utsjekk.yml"))),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
        )

        service.ingestCommits("navikt/helved-utbetaling", now.minusSeconds(3600))
        service.ingestWorkflow("navikt/helved-utbetaling", "utsjekk", "utsjekk.yml", now.minusSeconds(3600))
        service.ingestCommits("navikt/helved-utbetaling", now.minusSeconds(3600))
        service.ingestWorkflow("navikt/helved-utbetaling", "utsjekk", "utsjekk.yml", now.minusSeconds(3600))

        assertEquals(1, transaction { AuditCommit.query("SELECT * FROM audit_commit") }.size)
        assertEquals(2, transaction { AuditWorkflowExecution.query("SELECT * FROM audit_workflow_execution") }.size)
        assertEquals(2, transaction { AuditWorkflowSource.query("SELECT * FROM audit_workflow_source") }.size)
        assertEquals(2, transaction { AuditWorkflowJob.query("SELECT * FROM audit_workflow_job") }.size)
        assertEquals(2, transaction { AuditWorkflowStep.query("SELECT * FROM audit_workflow_step") }.size)
        assertEquals(1, transaction { AuditWorkflowExecutionCommit.query("SELECT * FROM audit_workflow_execution_commit") }.size)
    }

    @Test
    fun `populerer hele auditkjeden fra reelle GitHub-responser`() = runTest(TestRuntime.context) {
        val service = AuditService(
            fetcher = RealPayloadAuditFetcher(),
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", mapOf("abetal" to "abetal.yml"))),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
        )

        val since = Instant.parse("2026-09-01T00:00:00Z")
        service.ingestCommits("navikt/helved-utbetaling", since)
        service.ingestWorkflow("navikt/helved-utbetaling", "abetal", "abetal.yml", since)
        service.snapshotControls()

        val commits = transaction { AuditCommit.query("SELECT * FROM audit_commit ORDER BY committed_at") }
        assertEquals(3, commits.size)
        assertEquals(setOf(511L, 636L), commits.mapNotNull { it.taskNumber }.toSet())
        assertEquals("developer-two", commits.single { it.sha.startsWith("0fae") }.committerLogin)

        val attempts = transaction { AuditWorkflowExecution.attempts("navikt/helved-utbetaling", 34967033382) }
        assertEquals(1, attempts.size)
        val attempt = attempts.single()
        assertEquals(".github/workflows/abetal.yml", attempt.workflowPath)
        assertEquals("developer-two", attempt.actorLogin)

        val attemptId = assertNotNull(attempt.id)
        val definition = transaction { AuditWorkflowSource.find(attemptId) }
        assertNotNull(definition)
        assertEquals("name: abetal\njobs:\n  test:\n  deploy-prod:", definition.content)

        val jobs = transaction { AuditWorkflowJob.forExecution(attemptId) }
        assertEquals(setOf("package", "test", "deploy-dev", "deploy-prod"), jobs.map { it.name }.toSet())
        val steps = transaction {
            jobs.flatMap { job -> AuditWorkflowStep.forJob(assertNotNull(job.id)) }
        }
        assertEquals(5, steps.size)
        assertEquals(1, steps.count { it.conclusion == "skipped" })

        val deployedCommits = transaction { AuditWorkflowExecutionCommit.commitsForExecution(attemptId) }
        assertEquals(listOf("0fae4100f8aa7f3242fdfc58680d76cbc9ef9657"), deployedCommits.map { it.sha })

        val controls = transaction {
            AuditControlSnapshot.at("navikt/helved-utbetaling", "main", Instant.now().plusSeconds(1))
        }
        assertEquals(setOf("branch_protection", "repository_rulesets"), controls.map { it.controlType }.toSet())
        assertEquals(FetchStatus.FORBIDDEN, controls.single { it.controlType == "branch_protection" }.fetchStatus)
        assertEquals(FetchStatus.PRESENT, controls.single { it.controlType == "repository_rulesets" }.fetchStatus)
    }

    @Test
    fun `oppdaterer en administrert task-kommentar fra commits lagret av audit`() = runTest(TestRuntime.context) {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        val commentClient = TaskCommentClientFake()
        val service = AuditService(
            fetcher = FakeAuditFetcher(now),
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", emptyMap())),
            jdbcCtx = TestRuntime.context,
            taskRepository = "navikt/team-helved",
            taskComments = TaskCommentProjector(commentClient, TestRuntime.context),
        )

        service.ingestCommits("navikt/helved-utbetaling", now.minusSeconds(3600))
        service.ingestCommits("navikt/helved-utbetaling", now.minusSeconds(3600))

        assertEquals(1, commentClient.created.size)
        assertEquals(0, commentClient.updated.size)
        assertTrue(commentClient.created.single().body.orEmpty().contains("abc123"))
        assertTrue(commentClient.created.single().body.orEmpty().startsWith(TaskCommentProjector.MARKER))
    }
}

private class RealPayloadAuditFetcher : AuditFetcher {
    private val json = libs.kotlinx.KotlinxJson
    private val commits = json.decodeFromString<List<GithubCommit>>(GithubPayloads.commitsResponse)
        .map { CapturedCommit(it, json.encodeToJsonElement(it)) }
    private val run = json.decodeFromString<WorkflowRunsPage>(GithubPayloads.workflowResponse)
        .workflowRuns.single()
    private val jobs = json.decodeFromString<WorkflowJobsPage>(GithubPayloads.jobsResponse)
    private val rulesets = json.parseToJsonElement(GithubPayloads.rulesetsResponse)

    override suspend fun commits(repo: String, since: Instant) = commits
    override suspend fun commit(repo: String, sha: String) = commits.single { it.value.sha == sha }
    override suspend fun runs(repo: String, workflowFile: String, since: Instant) =
        listOf(CapturedWorkflowRun(run, json.encodeToJsonElement(run)))
    override suspend fun runAttempt(repo: String, runId: Long, attempt: Int) =
        CapturedWorkflowRun(run.copy(runAttempt = attempt), json.encodeToJsonElement(run.copy(runAttempt = attempt)))
    override suspend fun compareCommits(repo: String, base: String, head: String) = commits.filter { it.value.sha == head }
    override suspend fun jobs(repo: String, runId: Long, attempt: Int) =
        CapturedWorkflowJobs(jobs, json.encodeToJsonElement(jobs))
    override suspend fun workflowSource(repo: String, workflowPath: String, sha: String) = WorkflowSource(
        path = workflowPath,
        ref = sha,
        blobSha = "f00ba4",
        content = "name: abetal\njobs:\n  test:\n  deploy-prod:",
        raw = JsonObject(mapOf("sha" to JsonPrimitive("f00ba4"))),
    )
    override suspend fun branchProtection(repo: String, branch: String) =
        CapturedControl(FetchStatus.FORBIDDEN, JsonPrimitive(401))
    override suspend fun rulesets(repo: String) = CapturedControl(
        FetchStatus.PRESENT,
        JsonObject(mapOf("summaries" to rulesets, "details" to JsonArray(emptyList()))),
    )
}

private class FakeAuditFetcher(private val now: Instant) : AuditFetcher {
    var protection = JsonObject(mapOf("required_reviews" to JsonPrimitive(1)))
    var protectionStatus = FetchStatus.PRESENT
    var deployConclusion = "success"
    var branchProtectionCalls = 0
    var rulesetCalls = 0

    override suspend fun commits(repo: String, since: Instant) = listOf(
        CapturedCommit(GithubCommit(
            sha = "abc123",
            commit = CommitDetail(
                author = CommitAuthor(now),
                committer = CommitAuthor(now.plusSeconds(1)),
                message = "Audit #42",
                verification = CommitVerification(true, "valid"),
            ),
            author = GithubUser("dev"),
            committer = GithubUser("bot"),
        ), JsonObject(mapOf("sha" to JsonPrimitive("abc123"), "extra" to JsonPrimitive("preserved")))),
    )

    override suspend fun commit(repo: String, sha: String) = commits(repo, now).single { it.value.sha == sha }

    override suspend fun runs(repo: String, workflowFile: String, since: Instant) = listOf(
        CapturedWorkflowRun(WorkflowRun(
            id = 99,
            name = "CI",
            headSha = "abc123",
            headBranch = "main",
            event = "push",
            status = "completed",
            conclusion = "success",
            createdAt = now.minusSeconds(60),
            updatedAt = now,
            htmlUrl = "https://github.example/run/99",
            runAttempt = 2,
            path = ".github/workflows/utsjekk.yml",
            actor = GithubUser("dev"),
        ), JsonObject(mapOf("id" to JsonPrimitive(99), "extra" to JsonPrimitive("preserved")))),
    )

    override suspend fun runAttempt(repo: String, runId: Long, attempt: Int): CapturedWorkflowRun {
        val latest = runs(repo, "utsjekk.yml", now).single()
        return CapturedWorkflowRun(latest.value.copy(runAttempt = attempt), latest.raw)
    }

    override suspend fun compareCommits(repo: String, base: String, head: String) = commits(repo, now)

    override suspend fun jobs(repo: String, runId: Long, attempt: Int): CapturedWorkflowJobs {
        val jobs = if (attempt == 1) {
            listOf(job(1, "test", attempt, "gradle test"))
        } else {
            listOf(job(2, "deploy-prod", attempt, "deploy", deployConclusion))
        }
        return CapturedWorkflowJobs(WorkflowJobsPage(jobs = jobs), JsonObject(emptyMap()))
    }

    override suspend fun workflowSource(repo: String, workflowPath: String, sha: String) = WorkflowSource(
        path = workflowPath,
        ref = sha,
        blobSha = "blob",
        content = "name: utsjekk",
        raw = JsonObject(mapOf("sha" to JsonPrimitive("blob"))),
    )

    override suspend fun branchProtection(repo: String, branch: String): CapturedControl {
        branchProtectionCalls++
        return CapturedControl(protectionStatus, protection)
    }

    override suspend fun rulesets(repo: String): CapturedControl {
        rulesetCalls++
        return CapturedControl(FetchStatus.PRESENT, JsonObject(emptyMap()))
    }

    private fun job(id: Long, name: String, attempt: Int, stepName: String, conclusion: String = "success") = WorkflowJob(
        id = id,
        name = name,
        status = "completed",
        conclusion = conclusion,
        startedAt = now.minusSeconds(10),
        completedAt = now,
        runId = 99,
        runAttempt = attempt,
        headSha = "abc123",
        createdAt = now.minusSeconds(20),
        steps = listOf(WorkflowStep(stepName, "completed", "success", 1)),
    )
}

private class DeploymentRangeAuditFetcher : AuditFetcher {
    private val base = Instant.parse("2026-09-21T12:00:00Z")
    var currentRun = 1
    val comparisons = mutableListOf<Pair<String, String>>()

    override suspend fun commits(repo: String, since: Instant) = emptyList<CapturedCommit>()
    override suspend fun commit(repo: String, sha: String) = commit(sha, base.plusSeconds(currentRun.toLong()))
    override suspend fun runs(repo: String, workflowFile: String, since: Instant) = listOf(run(currentRun))
    override suspend fun runAttempt(repo: String, runId: Long, attempt: Int) = run(runId.toInt())
    override suspend fun compareCommits(repo: String, base: String, head: String): List<CapturedCommit> {
        comparisons.add(base to head)
        return listOf(commit("bbb", this.base.plusSeconds(2)), commit(head, this.base.plusSeconds(3)))
    }
    override suspend fun jobs(repo: String, runId: Long, attempt: Int) = CapturedWorkflowJobs(
        WorkflowJobsPage(jobs = listOf(WorkflowJob(
            id = runId,
            name = "deploy-prod",
            status = "completed",
            conclusion = "success",
            startedAt = base,
            completedAt = base.plusSeconds(runId),
            runId = runId,
            runAttempt = attempt,
            headSha = if (runId == 1L) "aaa" else "ccc",
            createdAt = base,
        ))),
        JsonObject(emptyMap()),
    )
    override suspend fun workflowSource(repo: String, workflowPath: String, sha: String) = WorkflowSource(
        workflowPath, sha, "blob-$sha", "name: utsjekk", JsonObject(emptyMap()),
    )
    override suspend fun branchProtection(repo: String, branch: String) = CapturedControl(FetchStatus.PRESENT, JsonObject(emptyMap()))
    override suspend fun rulesets(repo: String) = CapturedControl(FetchStatus.PRESENT, JsonObject(emptyMap()))

    private fun run(number: Int): CapturedWorkflowRun {
        val sha = if (number == 1) "aaa" else "ccc"
        val run = WorkflowRun(
            id = number.toLong(),
            name = "CI",
            headSha = sha,
            headBranch = "main",
            event = "push",
            status = "completed",
            conclusion = "success",
            createdAt = base.plusSeconds(number.toLong()),
            updatedAt = base.plusSeconds(number.toLong()),
            htmlUrl = "https://github.example/run/$number",
            path = ".github/workflows/utsjekk.yml",
        )
        return CapturedWorkflowRun(run, libs.kotlinx.KotlinxJson.encodeToJsonElement(run))
    }

    private fun commit(sha: String, at: Instant): CapturedCommit {
        val commit = GithubCommit(sha, CommitDetail(CommitAuthor(at), message = sha))
        return CapturedCommit(commit, libs.kotlinx.KotlinxJson.encodeToJsonElement(commit))
    }
}

private class LongRunningWorkflowFetcher(private val createdAt: Instant) : AuditFetcher {
    var completed = false
    val requestedSince = mutableListOf<Instant>()

    override suspend fun commits(repo: String, since: Instant) = emptyList<CapturedCommit>()
    override suspend fun commit(repo: String, sha: String) = error("No deploy commit expected")
    override suspend fun runs(repo: String, workflowFile: String, since: Instant): List<CapturedWorkflowRun> {
        requestedSince.add(since)
        if (createdAt.isBefore(since)) return emptyList()
        val run = WorkflowRun(
            id = 77,
            name = "CI",
            headSha = "abc123",
            headBranch = "main",
            event = "push",
            status = if (completed) "completed" else "in_progress",
            conclusion = if (completed) "success" else null,
            createdAt = createdAt,
            updatedAt = if (completed) createdAt.plusSeconds(3600) else createdAt.plusSeconds(60),
            htmlUrl = "https://github.example/run/77",
            path = ".github/workflows/utsjekk.yml",
        )
        return listOf(CapturedWorkflowRun(run, libs.kotlinx.KotlinxJson.encodeToJsonElement(run)))
    }
    override suspend fun runAttempt(repo: String, runId: Long, attempt: Int) = error("No re-run expected")
    override suspend fun compareCommits(repo: String, base: String, head: String) = emptyList<CapturedCommit>()
    override suspend fun jobs(repo: String, runId: Long, attempt: Int) = CapturedWorkflowJobs(
        WorkflowJobsPage(jobs = listOf(WorkflowJob(
            id = 77,
            name = "test",
            status = "completed",
            conclusion = "success",
            startedAt = createdAt,
            completedAt = createdAt.plusSeconds(3600),
            runId = runId,
            runAttempt = attempt,
            headSha = "abc123",
            createdAt = createdAt,
        ))),
        JsonObject(emptyMap()),
    )
    override suspend fun workflowSource(repo: String, workflowPath: String, sha: String) = WorkflowSource(
        workflowPath, sha, "blob", "name: utsjekk", JsonObject(emptyMap()),
    )
    override suspend fun branchProtection(repo: String, branch: String) = CapturedControl(FetchStatus.PRESENT, JsonObject(emptyMap()))
    override suspend fun rulesets(repo: String) = CapturedControl(FetchStatus.PRESENT, JsonObject(emptyMap()))
}
