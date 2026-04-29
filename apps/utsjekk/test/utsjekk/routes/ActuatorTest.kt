package utsjekk.routes

import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
}
