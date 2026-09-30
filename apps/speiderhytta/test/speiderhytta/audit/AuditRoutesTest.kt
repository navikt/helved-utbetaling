package speiderhytta.audit

import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import libs.jdbc.concurrency.transaction
import speiderhytta.TestRuntime
import speiderhytta.github.FetchStatus
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditRoutesTest {
    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `evidence bruker commits og kontroller fra attemptet som deployet`() = runTest(TestRuntime.context) {
        val deployedAt = Instant.parse("2026-09-21T12:00:00Z")
        val failedAt = deployedAt.plusSeconds(3600)
        val repository = "navikt/helved-utbetaling"
        transaction {
            AuditControlSnapshot(
                repository = repository,
                branch = "main",
                controlType = "branch_protection",
                fetchStatus = FetchStatus.PRESENT,
                payload = JsonObject(mapOf("reviews" to JsonPrimitive(1))),
                payloadSha256 = "control-1",
                capturedAt = deployedAt.minusSeconds(1),
            ).insert()
            AuditControlSnapshot(
                repository = repository,
                branch = "main",
                controlType = "branch_protection",
                fetchStatus = FetchStatus.PRESENT,
                payload = JsonObject(mapOf("reviews" to JsonPrimitive(2))),
                payloadSha256 = "control-2",
                capturedAt = failedAt.minusSeconds(1),
            ).insert()
            val deployed = workflow(repository, attempt = 1, updatedAt = deployedAt)
            val failed = workflow(repository, attempt = 2, updatedAt = failedAt)
            deployed.insert()
            failed.insert()
            val deployedId = AuditWorkflowExecution.find(repository, 99, 1)!!.id!!
            val failedId = AuditWorkflowExecution.find(repository, 99, 2)!!.id!!
            job(deployedId, 1, "success").insert()
            job(failedId, 2, "failure").insert()
            val commit = AuditCommit(
                repository = repository,
                sha = "abc123",
                message = "deploy",
                authoredAt = deployedAt,
                committedAt = deployedAt,
                parents = JsonObject(emptyMap()),
                rawMetadata = JsonObject(emptyMap()),
            )
            commit.insert()
            AuditWorkflowExecutionCommit(deployedId, AuditCommit.find(repository, "abc123")!!.id!!).insert()
        }

        val result = transaction { evidence(repository, 99) }

        assertEquals(listOf("abc123"), result!!.commits.map { it.sha })
        assertEquals(1, result.controls.single().payload.jsonObject["reviews"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `workflow-listen aggregerer attempts og deploy-resultat`() = runTest(TestRuntime.context) {
        val repository = "navikt/helved-utbetaling"
        val firstUpdated = Instant.parse("2026-09-21T12:00:00Z")
        val latestUpdated = firstUpdated.plusSeconds(600)
        transaction {
            workflow(repository, attempt = 1, updatedAt = firstUpdated).copy(conclusion = "failure").insert()
            workflow(repository, attempt = 2, updatedAt = latestUpdated).copy(conclusion = "success").insert()
            val latestId = AuditWorkflowExecution.find(repository, 99, 2)!!.id!!
            job(latestId, 2, "success").insert()
            AuditCommit(
                repository = repository,
                sha = "abc123",
                message = "fiks utbetaling",
                authoredAt = latestUpdated,
                committedAt = latestUpdated,
                parents = JsonObject(emptyMap()),
                rawMetadata = JsonObject(emptyMap()),
            ).insert()
            AuditWorkflowExecutionCommit(latestId, AuditCommit.find(repository, "abc123")!!.id!!).insert()
        }

        val response = TestRuntime.httpClient.get(
            "/audit/workflows/navikt/helved-utbetaling?app=utsjekk&from=2026-09-21T11:00:00Z&to=2026-09-21T13:00:00Z&limit=10",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val rows = response.body<List<WorkflowRunSummary>>()
        assertEquals(1, rows.size)
        rows.single().let { row ->
            assertEquals(99, row.runId)
            assertEquals(2, row.attemptCount)
            assertEquals(2, row.latestAttempt)
            assertEquals("success", row.conclusion)
            assertEquals("success", row.deployProdConclusion)
            assertTrue(row.hasPreviousFailures)
            assertEquals("fiks utbetaling", row.commitMessage)
            assertEquals(latestUpdated, row.updatedAt)
        }
    }

    @Test
    fun `workflow-listen sorterer nyeste først og filtrerer app`() = runTest(TestRuntime.context) {
        val repository = "navikt/helved-utbetaling"
        val older = Instant.parse("2026-09-21T12:00:00Z")
        val newer = older.plusSeconds(600)
        transaction {
            workflow(repository, attempt = 1, updatedAt = older).insert()
            workflow(repository, attempt = 1, updatedAt = newer).copy(
                app = "abetal",
                workflowFile = "abetal.yml",
                runId = 100,
                headSha = "def456",
                runUrl = "https://github.example/run/100",
            ).insert()
        }

        val all = TestRuntime.httpClient.get("/audit/workflows/navikt/helved-utbetaling")
            .body<List<WorkflowRunSummary>>()
        val filtered = TestRuntime.httpClient.get("/audit/workflows/navikt/helved-utbetaling?app=utsjekk")
            .body<List<WorkflowRunSummary>>()

        assertEquals(listOf(100L, 99L), all.map { it.runId })
        assertEquals(listOf(99L), filtered.map { it.runId })
    }

    @Test
    fun `workflow-listen avviser ugyldig tidsintervall`() = runTest {
        val invalidTimestamp = TestRuntime.httpClient.get(
            "/audit/workflows/navikt/helved-utbetaling?from=ikke-et-tidspunkt",
        )
        val reversed = TestRuntime.httpClient.get(
            "/audit/workflows/navikt/helved-utbetaling?from=2026-09-22T00:00:00Z&to=2026-09-21T00:00:00Z",
        )

        assertEquals(HttpStatusCode.BadRequest, invalidTimestamp.status)
        assertEquals(HttpStatusCode.BadRequest, reversed.status)
    }

    private fun workflow(repository: String, attempt: Int, updatedAt: Instant) = AuditWorkflowExecution(
        repository = repository,
        app = "utsjekk",
        workflowFile = "utsjekk.yml",
        runId = 99,
        runAttempt = attempt,
        headSha = "abc123",
        event = "push",
        status = "completed",
        conclusion = if (attempt == 1) "success" else "failure",
        createdAt = updatedAt.minusSeconds(60),
        updatedAt = updatedAt,
        runUrl = "https://github.example/run/99",
        rawMetadata = JsonObject(emptyMap()),
    )

    private fun job(workflowId: Long, githubJobId: Long, conclusion: String) = AuditWorkflowJob(
        workflowExecutionId = workflowId,
        githubJobId = githubJobId,
        name = "deploy-prod",
        status = "completed",
        conclusion = conclusion,
        createdAt = Instant.parse("2026-09-21T11:00:00Z"),
        startedAt = null,
        completedAt = null,
        rawMetadata = JsonObject(emptyMap()),
    )
}
