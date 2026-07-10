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
        // exists in the metric registry.
        httpClient.get("/actuator/ready")

        // Micrometer records the timer asynchronously after the response is sent.
        // Poll until the metric appears.
        var body = ""
        repeat(100) {
            body = httpClient.get("/actuator/metric").bodyAsText()
            if (body.contains("ktor_http_server_requests_seconds")) return@runTest run {
                assertTrue(body.contains("logback_events_total"))
                assertTrue(body.contains("jvm_threads_states_threads"))
            }
            Thread.sleep(10)
        }

        assertTrue(body.contains("logback_events_total"))
        assertTrue(body.contains("ktor_http_server_requests_seconds"), "metric not found after polling")
        assertTrue(body.contains("jvm_threads_states_threads"))
    }
}
