package utsjekk.routes

import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActuatorTest {

    @Test
    fun ready() = runTest {
        val res = httpClient.get("/actuator/ready")
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun live() = runTest {
        val res = httpClient.get("/actuator/live")
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun meter() = runTest {
        // Trigger at least one server request so ktor_http_server_requests_seconds
        // exists in the metric registry. Under class-concurrency this test may
        // run before any other test class has issued a request.
        httpClient.get("/actuator/ready")

        val res = httpClient.get("/actuator/metric")
        assertEquals(HttpStatusCode.OK, res.status)

        val body = res.bodyAsText()
        assertTrue(body.contains("logback_events_total"))
        assertTrue(body.contains("ktor_http_server_requests_seconds"))
        assertTrue(body.contains("jvm_threads_states_threads"))
    }
}
