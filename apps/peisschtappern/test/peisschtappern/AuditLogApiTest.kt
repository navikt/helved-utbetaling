package peisschtappern

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuditLogApiTest {
    @Test
    fun `can read audit logs`() = runTest(TestRuntime.context) {
        val entry = AuditLogEntry("2026-09-29T08:00:00Z", "INFO", "projects/p/logs/audit", "{}")
        TestRuntime.auditLogs.entries.apply { clear(); add(entry) }

        val res = TestRuntime.ktor.httpClient.get("/api/audit-logs") {
            url {
                parameters.append("filter", "severity>=INFO")
                parameters.append("pageSize", "10")
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf(entry), res.body<AuditLogPage>().entries)
        assertEquals("severity>=INFO", TestRuntime.auditLogs.lastFilter)
        assertEquals(10, TestRuntime.auditLogs.lastPageSize)
    }

    @Test
    fun `requires token`() = runTest(TestRuntime.context) {
        val res = TestRuntime.ktor.httpClient.get("/api/audit-logs")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }
}
