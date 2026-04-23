package speiderhytta.dora

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import speiderhytta.CodeRepoConfig
import speiderhytta.Metrics
import speiderhytta.TestRuntime
import speiderhytta.github.WorkflowJob
import speiderhytta.github.WorkflowRun
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeployServiceTest {

    private val metrics = Metrics(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))

    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `successful deploy is recorded with lead time from commit author timestamp`() = runTest(TestRuntime.context) {
        val finished = Instant.parse("2026-04-22T12:00:00Z")
        val started = finished.minusSeconds(180)
        val authored = finished.minusSeconds(900)

        val service = service(
            runs = listOf(run(id = 100, sha = "abc", finished = finished)),
            jobs = mapOf(100L to listOf(job("deploy-prod", "success", started, finished))),
            commitTimes = mapOf("abc" to authored),
        )
        service.ingest(finished.minusSeconds(3600))

        val stored = transaction { Deployment.findBySha("utsjekk", "abc", "prod-gcp") }
        assertNotNull(stored)
        assertEquals("success", stored.outcome)
        assertEquals(100L, stored.runId)
        assertEquals(900L, stored.leadTimeSeconds)
        assertEquals(authored, stored.commitTs)
    }

    @Test
    fun `failed deploy is recorded with zero lead time`() = runTest(TestRuntime.context) {
        val finished = Instant.parse("2026-04-22T12:00:00Z")
        val started = finished.minusSeconds(60)

        val service = service(
            runs = listOf(run(id = 200, sha = "bad", finished = finished, conclusion = "failure")),
            jobs = mapOf(200L to listOf(job("deploy-prod", "failure", started, finished))),
            commitTimes = emptyMap(), // commit lookup must NOT happen for failures
        )
        service.ingest(finished.minusSeconds(3600))

        val stored = transaction { Deployment.findBySha("utsjekk", "bad", "prod-gcp") }
        assertNotNull(stored)
        assertEquals("failure", stored.outcome)
        assertEquals(0L, stored.leadTimeSeconds)
    }

    @Test
    fun `cancelled deploy is recorded as cancelled`() = runTest(TestRuntime.context) {
        val finished = Instant.parse("2026-04-22T12:00:00Z")
        val started = finished.minusSeconds(10)

        val service = service(
            runs = listOf(run(id = 300, sha = "stop", finished = finished, conclusion = "cancelled")),
            jobs = mapOf(300L to listOf(job("deploy-prod", "cancelled", started, finished))),
        )
        service.ingest(finished.minusSeconds(3600))

        val stored = transaction { Deployment.findBySha("utsjekk", "stop", "prod-gcp") }
        assertNotNull(stored)
        assertEquals("cancelled", stored.outcome)
    }

    @Test
    fun `still-running workflow run is skipped`() = runTest(TestRuntime.context) {
        val now = Instant.parse("2026-04-22T12:00:00Z")
        val service = service(
            runs = listOf(run(id = 400, sha = "wip", finished = now, conclusion = null)),
            jobs = emptyMap(),
        )
        service.ingest(now.minusSeconds(3600))

        val stored = transaction { Deployment.findBySha("utsjekk", "wip", "prod-gcp") }
        assertNull(stored, "in-progress runs should be left for a later poll")
    }

    @Test
    fun `run without deploy-prod job is skipped`() = runTest(TestRuntime.context) {
        // snickerboa's real workflow has no deploy-prod job — the service
        // should silently produce no rows rather than fail.
        val now = Instant.parse("2026-04-22T12:00:00Z")
        val service = service(
            codeRepos = listOf(CodeRepoConfig("navikt/helved-utbetaling", mapOf("snickerboa" to "snickerboa.yml"))),
            runs = listOf(run(id = 500, sha = "xyz", finished = now)),
            jobs = mapOf(500L to listOf(job("build", "success", now.minusSeconds(60), now))),
        )
        service.ingest(now.minusSeconds(3600))

        val stored = transaction { Deployment.findBySha("snickerboa", "xyz", "prod-gcp") }
        assertNull(stored)
    }

    @Test
    fun `re-run of same sha produces a second row with its own runId`() = runTest(TestRuntime.context) {
        val first = Instant.parse("2026-04-22T10:00:00Z")
        val second = Instant.parse("2026-04-22T11:00:00Z")
        val service = service(
            runs = listOf(
                run(id = 1, sha = "same", finished = first, conclusion = "failure"),
                run(id = 2, sha = "same", finished = second, conclusion = "success"),
            ),
            jobs = mapOf(
                1L to listOf(job("deploy-prod", "failure", first.minusSeconds(60), first)),
                2L to listOf(job("deploy-prod", "success", second.minusSeconds(60), second)),
            ),
            commitTimes = mapOf("same" to first.minusSeconds(300)),
        )
        service.ingest(first.minusSeconds(3600))

        val all = transaction { Deployment.selectAllFor("utsjekk", "prod-gcp", first.minusSeconds(3600)) }
        assertEquals(2, all.size, "both attempts should be stored as separate rows")
        assertEquals(setOf(1L, 2L), all.map { it.runId }.toSet())
    }

    @Test
    fun `peisen deploys via deploy-yaml workflow file in the helved-peisen repo`() = runTest(TestRuntime.context) {
        // Verifies the multi-repo loop AND that the .yaml extension (vs .yml)
        // for helved-peisen actually round-trips through the fetcher.
        val finished = Instant.parse("2026-04-22T12:00:00Z")
        val authored = finished.minusSeconds(600)

        val backendRepo = CodeRepoConfig("navikt/helved-utbetaling", mapOf("utsjekk" to "utsjekk.yml"))
        val frontendRepo = CodeRepoConfig("navikt/helved-peisen", mapOf("peisen" to "deploy.yaml"))

        // Track what (repo, file) pairs get queried so we catch silent
        // misrouting (e.g. asking helved-utbetaling for deploy.yaml).
        val queried = mutableListOf<Pair<String, String>>()
        val fetcher = object : DeployFetcher {
            override suspend fun runs(repo: String, workflowFile: String, since: Instant): List<WorkflowRun> {
                queried += repo to workflowFile
                return when (repo to workflowFile) {
                    "navikt/helved-utbetaling" to "utsjekk.yml" ->
                        listOf(run(id = 7001, sha = "backend", finished = finished))
                    "navikt/helved-peisen" to "deploy.yaml" ->
                        listOf(run(id = 7002, sha = "frontend", finished = finished))
                    else -> emptyList()
                }
            }
            override suspend fun jobs(repo: String, runId: Long): List<WorkflowJob> = listOf(
                job("deploy-prod", "success", finished.minusSeconds(60), finished),
            )
            override suspend fun commitAuthorTime(repo: String, sha: String): Instant? = authored
        }
        val service = DeployService(fetcher, metrics, codeRepos = listOf(backendRepo, frontendRepo))
        service.ingest(finished.minusSeconds(3600))

        assertEquals(
            listOf("navikt/helved-utbetaling" to "utsjekk.yml", "navikt/helved-peisen" to "deploy.yaml"),
            queried,
        )

        val backend = transaction { Deployment.findBySha("utsjekk", "backend", "prod-gcp") }
        val frontend = transaction { Deployment.findBySha("peisen", "frontend", "prod-gcp") }
        assertNotNull(backend)
        assertNotNull(frontend)
        assertEquals(7001L, backend.runId)
        assertEquals(7002L, frontend.runId)
    }

    private fun service(
        runs: List<WorkflowRun>,
        jobs: Map<Long, List<WorkflowJob>>,
        commitTimes: Map<String, Instant> = emptyMap(),
        codeRepos: List<CodeRepoConfig> = listOf(
            CodeRepoConfig("navikt/helved-utbetaling", mapOf("utsjekk" to "utsjekk.yml")),
        ),
    ): DeployService {
        val fetcher = object : DeployFetcher {
            override suspend fun runs(repo: String, workflowFile: String, since: Instant): List<WorkflowRun> {
                val match = codeRepos.firstNotNullOfOrNull { cfg ->
                    cfg.apps.entries.firstOrNull { it.value == workflowFile && cfg.repo == repo }
                }
                return if (match != null) runs else emptyList()
            }
            override suspend fun jobs(repo: String, runId: Long): List<WorkflowJob> = jobs[runId].orEmpty()
            override suspend fun commitAuthorTime(repo: String, sha: String): Instant? = commitTimes[sha]
        }
        return DeployService(fetcher, metrics, codeRepos = codeRepos)
    }

    private fun run(
        id: Long,
        sha: String,
        finished: Instant,
        conclusion: String? = "success",
    ) = WorkflowRun(
        id = id,
        name = "ci",
        headSha = sha,
        headBranch = "main",
        event = "push",
        status = if (conclusion == null) "in_progress" else "completed",
        conclusion = conclusion,
        createdAt = finished.minusSeconds(300),
        updatedAt = finished,
        htmlUrl = "https://example/run/$id",
    )

    private fun job(name: String, conclusion: String, started: Instant, completed: Instant) = WorkflowJob(
        id = 1L,
        name = name,
        status = "completed",
        conclusion = conclusion,
        startedAt = started,
        completedAt = completed,
    )
}
