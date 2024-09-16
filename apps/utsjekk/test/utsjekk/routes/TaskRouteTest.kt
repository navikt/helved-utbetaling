package utsjekk.routes

import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class TaskRouteTest {

    @Test
    fun `get all tasks`() = runTest {
        val res = httpClient.get("/api/tasks") {
            bearerAuth(TestRuntime.azure.generateToken())
        }
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `update task not found`() = runTest {
        val res = httpClient.patch("/api/tasks/${UUID.randomUUID()}") {
            bearerAuth(TestRuntime.azure.generateToken())
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `get history of task`() = runTest {
        val res = httpClient.get("/api/tasks/${UUID.randomUUID()}/history") {
            bearerAuth(TestRuntime.azure.generateToken())
        }
        assertEquals(HttpStatusCode.OK, res.status)
    }


}