package speiderhytta.dora

import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import speiderhytta.KotlinxJson
import speiderhytta.TestRuntime
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val doraSummaryLens = KotlinxJson.autoBody<DoraSummary>().toLens()
private val deploymentListLens = KotlinxJson.autoBody<List<Deployment>>().toLens()

class DoraRoutesTest {
    @AfterTest
    fun reset() = TestRuntime.reset()

    @Test
    fun `returnerer DORA-sammendrag for kjent app`() = runTest(TestRuntime.context) {
        val now = Instant.now()
        transaction {
            deployment("success", 1, now.minusSeconds(60)).insert()
        }

        val response = TestRuntime.http(Request(Method.GET, "/dora/abetal"))

        assertEquals(Status.OK, response.status)
        val summary = doraSummaryLens(response)
        assertEquals("abetal", summary.app)
        assertEquals(1, summary.deploymentCount)
    }

    @Test
    fun `avviser ukjent app`() {
        val response = TestRuntime.http(Request(Method.GET, "/dora/ukjent"))

        assertEquals(Status.NOT_FOUND, response.status)
        assertEquals("unknown app", response.bodyString())
    }

    @Test
    fun `deployments returnerer bare vellykkede forsøk og følger limit`() = runTest(TestRuntime.context) {
        val now = Instant.now()
        transaction {
            deployment("success", 1, now.minusSeconds(60)).insert()
            deployment("success", 2, now.minusSeconds(120)).insert()
            deployment("failure", 3, now.minusSeconds(30)).insert()
        }

        val response = TestRuntime.http(
            Request(Method.GET, "/dora/abetal/deployments?since=${now.minusSeconds(3600)}&limit=1"),
        )

        assertEquals(Status.OK, response.status)
        val deployments = deploymentListLens(response)
        assertEquals(1, deployments.size)
        assertEquals("success", deployments.single().outcome)
        assertEquals(1L, deployments.single().runId)
    }

    private fun deployment(outcome: String, runId: Long, finished: Instant) = Deployment(
        app = "abetal",
        sha = "sha-$runId",
        env = "prod-gcp",
        commitTs = finished.minusSeconds(120),
        deployStartedTs = finished.minusSeconds(30),
        deployFinishedTs = finished,
        leadTimeSeconds = if (outcome == "success") 120 else 0,
        outcome = outcome,
        runId = runId,
        runUrl = "https://github.test/runs/$runId",
    )
}
