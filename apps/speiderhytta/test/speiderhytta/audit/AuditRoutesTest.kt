package speiderhytta.audit

import io.ktor.client.request.get
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

class AuditRoutesTest {
    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `task-ruten godtar konfigurert task-repository`() = runTest {
        val response = TestRuntime.httpClient.get("/audit/tasks/navikt/team-helved/633")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `task-ruten avviser code repository`() = runTest {
        val response = TestRuntime.httpClient.get("/audit/tasks/navikt/helved-utbetaling/633")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

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
