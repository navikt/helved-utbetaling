package peisschtappern

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.*

class AuditLogApiTest {
    @Test
    fun `can read audit logs`() = runTest(TestRuntime.context) {
        val entry = AuditLogEntry("2026-09-29T08:00:00Z", "INFO", "projects/p/logs/audit", "{}")
        TestRuntime.auditLogs.entries[TestRuntime.auditLogResource] = mutableListOf(entry)

        val res = TestRuntime.ktor.httpClient.get("/api/audit-logs") {
            url {
                parameters.append("fom", "2026-09-29T00:00:00Z")
                parameters.append("tom", "2026-09-30T00:00:00Z")
                parameters.append("filter", "severity>=INFO")
                parameters.append("pageSize", "10")
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf(entry), res.body<AuditLogPage>().entries)
        assertEquals(TestRuntime.auditLogResource, TestRuntime.auditLogs.lastResourceName)
        assertEquals(Instant.parse("2026-09-29T00:00:00Z"), TestRuntime.auditLogs.lastFom)
        assertEquals(Instant.parse("2026-09-30T00:00:00Z"), TestRuntime.auditLogs.lastTom)
        assertNull(TestRuntime.auditLogs.lastFilter)
        assertEquals(10, TestRuntime.auditLogs.lastPageSize)
    }

    @Test
    fun `requires token`() = runTest(TestRuntime.context) {
        val res = TestRuntime.ktor.httpClient.get("/api/audit-logs")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `developer audit logs only query personal database users`() = runTest(TestRuntime.context) {
        val entry = AuditLogEntry("2026-06-16T12:45:13.031Z", "INFO", "projects/p/logs/audit", "{}")
        TestRuntime.auditLogs.entries[TestRuntime.databaseAuditLogResource] = mutableListOf(entry)

        val res = TestRuntime.ktor.httpClient.get("/api/audit-logs/database") {
            url {
                parameters.append("fom", "2026-06-16T00:00:00Z")
                parameters.append("tom", "2026-06-17T00:00:00Z")
                parameters.append("pageSize", "10")
                parameters.append("pageToken", "next")
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf(entry), res.body<AuditLogPage>().entries)
        assertEquals(TestRuntime.databaseAuditLogResource, TestRuntime.auditLogs.lastResourceName)
        assertEquals(
            """resource.type="cloudsql_database" AND protoPayload.methodName="cloudsql.instances.query" AND protoPayload.request.user=~"(?i)@nav[.]no$" """.trim(),
            TestRuntime.auditLogs.lastFilter
        )
        assertEquals(Instant.parse("2026-06-16T00:00:00Z"), TestRuntime.auditLogs.lastFom)
        assertEquals(Instant.parse("2026-06-17T00:00:00Z"), TestRuntime.auditLogs.lastTom)
        assertEquals(10, TestRuntime.auditLogs.lastPageSize)
        assertEquals("next", TestRuntime.auditLogs.lastPageToken)
    }

    @Test
    fun `developer audit logs enforce filter without optional query`() = runTest(TestRuntime.context) {
        val res = TestRuntime.ktor.httpClient.get("/api/audit-logs/database") {
            bearerAuth(TestRuntime.azure.generateToken())
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(TestRuntime.databaseAuditLogResource, TestRuntime.auditLogs.lastResourceName)
        assertEquals(
            """resource.type="cloudsql_database" AND protoPayload.methodName="cloudsql.instances.query" AND protoPayload.request.user=~"(?i)@nav[.]no$" """.trim(),
            TestRuntime.auditLogs.lastFilter
        )
    }

    @Test
    fun `developer audit logs require token`() = runTest(TestRuntime.context) {
        val res = TestRuntime.ktor.httpClient.get("/api/audit-logs/database")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `each audit route is registered independently`() {
        testApplication {
            application {
                install(ContentNegotiation) { json(libs.kotlinx.KotlinxJson) }
                routing { auditLogs(AuditLogReaderFake(), TestRuntime.auditLogResource, null) }
            }

            assertEquals(HttpStatusCode.OK, client.get("/api/audit-logs").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/api/audit-logs/database").status)
        }

        testApplication {
            application {
                install(ContentNegotiation) { json(libs.kotlinx.KotlinxJson) }
                routing { auditLogs(AuditLogReaderFake(), null, TestRuntime.databaseAuditLogResource) }
            }

            assertEquals(HttpStatusCode.NotFound, client.get("/api/audit-logs").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/audit-logs/database").status)
        }
    }
}
