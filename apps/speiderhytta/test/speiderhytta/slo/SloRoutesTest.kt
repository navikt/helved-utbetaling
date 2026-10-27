package speiderhytta.slo

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

private val snapshotListLens = KotlinxJson.autoBody<List<SloSnapshot>>().toLens()

class SloRoutesTest {
    @AfterTest
    fun reset() = TestRuntime.reset()

    @Test
    fun `history behandles som historikk og ikke SLO-navn`() = runTest(TestRuntime.context) {
        val capturedAt = Instant.parse("2026-10-19T12:00:00Z")
        transaction {
            SloSnapshot(
                app = "utsjekk",
                sloName = "availability",
                objective = 99.9,
                sliValue = 0.0001,
                errorBudgetRemaining = 0.9,
                capturedAt = capturedAt,
            ).insert()
        }

        val response = TestRuntime.http(
            Request(Method.GET, "/slo/utsjekk/history?since=2026-10-19T00:00:00Z")
        )

        assertEquals(Status.OK, response.status)
        val snapshots = snapshotListLens(response)
        assertEquals(1, snapshots.size)
        assertEquals("availability", snapshots.single().sloName)
        assertEquals(capturedAt, snapshots.single().capturedAt)
    }
}
