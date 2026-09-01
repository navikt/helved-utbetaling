package simulering

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DryrunAuthTest {

    @BeforeEach
    fun setup() {
        TestRuntime.reset()
    }

    @Test
    fun `401 when no Authorization header`() {
        val response = TestRuntime.app(
            Request(Method.POST, "/api/simulering")
                .header("Content-Type", "application/json")
                .body("{}")
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `401 when invalid token`() {
        val response = TestRuntime.app(
            Request(Method.POST, "/api/simulering")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer garbage.token.here")
                .body("{}")
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `403 when unknown client`() {
        val token = TestRuntime.generateToken(azpName = "unknown-app")
        val response = TestRuntime.app(
            Request(Method.POST, "/api/simulering")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .body("{}")
        )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `known client passes auth for simulering v3`() {
        val token = TestRuntime.generateToken(azpName = "tilleggsstonader-sak")
        val response = TestRuntime.app(
            Request(Method.POST, "/api/simulering")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .body("{}")
        )
        // Auth passed — body parsing fails, so we get 500, not 401/403
        val authStatuses = setOf(Status.UNAUTHORIZED, Status.FORBIDDEN)
        assert(response.status !in authStatuses) {
            "Expected auth to pass, but got ${response.status}"
        }
    }

    @Test
    fun `401 on dryrun endpoint without token`() {
        val response = TestRuntime.app(
            Request(Method.POST, "/api/simulering")
                .header("Content-Type", "application/json")
                .body("{}")
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `403 on dryrun endpoint with wrong client`() {
        val token = TestRuntime.generateToken(azpName = "wrong-app")
        val response = TestRuntime.app(
            Request(Method.POST, "/api/simulering")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .body("{}")
        )
        assertEquals(Status.FORBIDDEN, response.status)
    }
}
