package speiderhytta.audit

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import libs.jdbc.concurrency.transaction
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import speiderhytta.KotlinxJson
import speiderhytta.TestRuntime
import speiderhytta.github.FetchStatus
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val summaryListLens = KotlinxJson.autoBody<List<WorkflowRunSummary>>().toLens()

class AuditRoutesTest {
    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `rapportens JSON inkluderer body og not found men utelater manglende snapshots`() = runTest(TestRuntime.context) {
        val now = Instant.parse("2026-11-18T12:00:00Z")
        val repository = "navikt/helved-utbetaling"
        transaction {
            workflow(repository, attempt = 1, updatedAt = now).insert()
            val executionId = AuditWorkflowExecution.find(repository, 99, 1)!!.id!!
            job(executionId, 1, "success").insert()
            (1..3).forEach { number ->
                AuditCommit(
                    repository = repository,
                    sha = "report-$number",
                    taskRepository = "navikt/team-helved",
                    taskNumber = 42,
                    message = "#42",
                    authoredAt = now,
                    committedAt = now,
                    parents = JsonArray(emptyList()),
                    rawMetadata = JsonObject(emptyMap())
                ).insert()
                val commitId = AuditCommit.find(repository, "report-$number")!!.id!!
                AuditWorkflowExecutionCommit(executionId, commitId).insert()
                if (number < 3) {
                    AuditTaskSnapshot(
                        commitId = commitId,
                        taskRepository = "navikt/team-helved",
                        taskNumber = 42,
                        fetchStatus = if (number == 1) "PRESENT" else "NOT_FOUND",
                        title = if (number == 1) "Task" else null,
                        state = if (number == 1) "open" else null,
                        labels = emptyList(),
                        capturedAt = now,
                        body = if (number == 1) "## Formål\nBeskrivelse med \"anførselstegn\"" else null
                    ).insert()
                }
            }
        }

        val handler = auditRoutes(TestRuntime.context, setOf(repository))
        val response = handler(Request(Method.GET, "/audit/report/$repository/workflows/99"))
        assertEquals(Status.OK, response.status)
        val json = libs.kotlinx.KotlinxJson.parseToJsonElement(response.bodyString()).jsonObject
        assertEquals(3, json.getValue("commits").jsonArray.size)
        val tasks = json.getValue("tasks").jsonArray.map { it.jsonObject }
        assertEquals(2, tasks.size)
        val present = tasks.single { it.getValue("fetchStatus").jsonPrimitive.content == "PRESENT" }
        assertEquals("## Formål\nBeskrivelse med \"anførselstegn\"", present.getValue("body").jsonPrimitive.content)
        val missing = tasks.single { it.getValue("fetchStatus").jsonPrimitive.content == "NOT_FOUND" }
        assertEquals(JsonNull, missing["body"] ?: JsonNull)
        assertEquals(setOf("42"), tasks.map { it.getValue("taskNumber").jsonPrimitive.content }.toSet())
        assertEquals(2, tasks.map { it.getValue("commitId").jsonPrimitive.content }.toSet().size)
    }

    @Test
    fun `report bruker commits og kontroller fra attemptet som deployet`() = runTest(TestRuntime.context) {
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

        val result = transaction { report(repository, 99) }

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

        val response = TestRuntime.http(
            Request(
                Method.GET,
                "/audit/workflows/navikt/helved-utbetaling?app=utsjekk&from=2026-09-21T11:00:00Z&to=2026-09-21T13:00:00Z&limit=10",
            ),
        )

        assertEquals(Status.OK, response.status)
        val rows = summaryListLens(response)
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

        val all = summaryListLens(TestRuntime.http(Request(Method.GET, "/audit/workflows/navikt/helved-utbetaling")))
        val filtered = summaryListLens(TestRuntime.http(Request(Method.GET, "/audit/workflows/navikt/helved-utbetaling?app=utsjekk")))

        assertEquals(listOf(100L, 99L), all.map { it.runId })
        assertEquals(listOf(99L), filtered.map { it.runId })
    }

    @Test
    fun `workflow-listen avviser ugyldig tidsintervall`() = runTest {
        val invalidTimestamp = TestRuntime.http(
            Request(Method.GET, "/audit/workflows/navikt/helved-utbetaling?from=ikke-et-tidspunkt"),
        )
        val reversed = TestRuntime.http(
            Request(Method.GET, "/audit/workflows/navikt/helved-utbetaling?from=2026-09-22T00:00:00Z&to=2026-09-21T00:00:00Z"),
        )

        assertEquals(Status.BAD_REQUEST, invalidTimestamp.status)
        assertEquals(Status.BAD_REQUEST, reversed.status)
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
