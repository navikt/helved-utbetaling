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
    fun health() = runTest {
        val res = httpClient.get("/actuator/health")
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun meter() = runTest {
        val res = httpClient.get("/actuator/metric")
        assertEquals(HttpStatusCode.OK, res.status)

        val body = res.bodyAsText()
        assertTrue(body.contains("logback_events_total"))
        assertTrue(body.contains("ktor_http_server_requests_seconds"))
        assertTrue(body.contains("jvm_threads_states_threads"))
    }
}
