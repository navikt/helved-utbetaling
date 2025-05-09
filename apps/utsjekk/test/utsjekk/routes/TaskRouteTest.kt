package utsjekk.routes

import TestData
import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.task.Kind
import libs.task.Status
import libs.task.Tasks
import models.kontrakter.felles.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
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

    @Test
    fun `rerun task`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val taskId = Tasks.create(Kind.Iverksetting, iverksetting, null, objectMapper::writeValueAsString)

        val res = httpClient.put("/api/tasks/${taskId}/rerun") {
            bearerAuth(TestRuntime.azure.generateToken())
        }
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `rerun all tasks with status`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val scheduledFor = LocalDateTime.now().plusDays(1)

        val ids = listOf(
            Tasks.create(Kind.Iverksetting, iverksetting, scheduledFor, objectMapper::writeValueAsString),
            Tasks.create(Kind.Iverksetting, iverksetting, scheduledFor, objectMapper::writeValueAsString),
            Tasks.create(Kind.Iverksetting, iverksetting, scheduledFor, objectMapper::writeValueAsString).also {
                Tasks.complete(it)
            },
        )

        ids.mapNotNull { Tasks.forId(it) }.forEach {
            assertEquals(scheduledFor.toLocalDate(), it.scheduledFor.toLocalDate())
        }

        val res = httpClient.put("/api/tasks/rerun") {
            url {
                parameters.append("status", Status.IN_PROGRESS.name)
            }
            bearerAuth(TestRuntime.azure.generateToken())
        }

        assertEquals(HttpStatusCode.OK, res.status)

        ids.mapNotNull { Tasks.forId(it) }.filter { it.status == Status.IN_PROGRESS && it.kind == Kind.Iverksetting }.forEach {
            assertTrue(it.scheduledFor < scheduledFor)
        }

        ids.mapNotNull { Tasks.forId(it) }.filter { it.status == Status.COMPLETE && it.kind == Kind.Iverksetting }.forEach {
            assertEquals(scheduledFor.toLocalDate(), it.scheduledFor.toLocalDate())
        }
    }

    @Test
    fun `rerun all tasks with kind`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragIdDto = TestData.dto.oppdragId(iverksetting)
        val scheduledFor = LocalDateTime.now().plusDays(1)

        val ids = listOf(
            Tasks.create(Kind.SjekkStatus, oppdragIdDto, scheduledFor, objectMapper::writeValueAsString),
            Tasks.create(Kind.SjekkStatus, oppdragIdDto, scheduledFor, objectMapper::writeValueAsString).also {
                Tasks.complete(it)
            },
        )

        ids.mapNotNull { Tasks.forId(it) }.filter { it.kind == Kind.SjekkStatus }.forEach {
            assertEquals(scheduledFor.toLocalDate(), it.scheduledFor.toLocalDate())
        }

        val res = httpClient.put("/api/tasks/rerun") {
            url {
                parameters.append("status", Status.IN_PROGRESS.name)
                parameters.append("kind", Kind.SjekkStatus.name)
            }
            bearerAuth(TestRuntime.azure.generateToken())
        }

        assertEquals(HttpStatusCode.OK, res.status)

        ids.mapNotNull { Tasks.forId(it) }.filter { it.kind == Kind.SjekkStatus && it.status == Status.IN_PROGRESS }.forEach {
            assertTrue(it.scheduledFor < scheduledFor)
        }

        ids.mapNotNull { Tasks.forId(it) }.filter { it.kind == Kind.SjekkStatus && it.status == Status.COMPLETE }.forEach {
            assertEquals(scheduledFor.toLocalDate(), it.scheduledFor.toLocalDate())
        }
    }
}
