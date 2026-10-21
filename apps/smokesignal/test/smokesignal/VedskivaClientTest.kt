package smokesignal

import kotlinx.coroutines.runBlocking
import libs.kotlinx.KotlinxJson
import models.ApiError
import org.http4k.core.*
import java.time.LocalDate
import kotlin.test.*

class VedskivaClientTest {
    private val today = LocalDate.of(2026, 2, 10)
    private val range = AvstemmingRequest(today, today.minusDays(1).atStartOfDay(), today.atStartOfDay().minusNanos(1))

    @Test
    fun `prøver serverfeil på nytt med begrenset antall forsøk`() {
        for (status in listOf(Status.SERVICE_UNAVAILABLE, Status.BAD_REQUEST)) {
            var calls = 0
            val delays = mutableListOf<Long>()
            val client = retryingHttpClient(
                client = { calls++; Response(status) },
                pause = { delays.add(it) }
            )
            assertEquals(status, client(Request(Method.POST, "http://localhost")).status)
            assertEquals(if (status.serverError) 4 else 1, calls)
            assertEquals(if (status.serverError) listOf(1000L, 2000L, 4000L) else emptyList(), delays)
        }
    }

    @Test
    fun `henter token og periode før avstemming med samme token`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = VedskivaClient(TestRuntime.config, client = { request ->
            requests.add(request)
            when (requests.size) {
                1 -> Response(Status.OK).body("""{"expires_in":3600,"access_token":"token"}""")
                2 -> Response(Status.OK).body(KotlinxJson.encodeToString(range))
                else -> Response(Status.CONFLICT)
            }
        })
        val next = client.next()
        client.signal(next)
        assertEquals(range, next)
        assertEquals(3, requests.size)
        assertContains(requests[0].bodyString(), "grant_type=client_credentials")
        assertContains(requests[0].bodyString(), "scope=scope")
        assertEquals("application/x-www-form-urlencoded", requests[0].header("Content-Type"))
        assertEquals("/api/next_range", requests[1].uri.path)
        assertEquals("/api/avstem", requests[2].uri.path)
        for (request in requests.drop(1)) {
            assertEquals("Bearer token", request.header("Authorization"))
            assertEquals("application/json", request.header("Content-Type"))
        }
        assertEquals(range, KotlinxJson.decodeFromString<AvstemmingRequest>(requests[2].bodyString()))
    }

    @Test
    fun `fornyer token før utløp`() = runBlocking {
        var tokens = 0
        val client = VedskivaClient(TestRuntime.config, client = { request ->
            if (request.uri.path == "/api/avstem") Response(Status.OK) else {
                tokens++
                Response(Status.OK).body("""{"expires_in":30,"access_token":"token"}""")
            }
        })
        client.signal(range)
        client.signal(range)
        assertEquals(2, tokens)
    }

    @Test
    fun `tokenfeil hindrer kall til vedskiva`() = runBlocking {
        for (status in listOf(Status.BAD_REQUEST, Status.TOO_MANY_REQUESTS, Status.SERVICE_UNAVAILABLE)) {
            var calls = 0
            val client = VedskivaClient(TestRuntime.config, client = {
                calls++
                Response(status)
            })
            val error = assertFailsWith<ApiError> { client.next() }
            assertEquals(503, error.statusCode)
            assertEquals(1, calls)
        }
    }

    @Test
    fun `feil ved henting av periode avbryter avstemming`() = runBlocking {
        var calls = 0
        val client = VedskivaClient(TestRuntime.config, client = {
            calls++
            if (calls == 1) Response(Status.OK).body("""{"expires_in":3600,"access_token":"token"}""")
            else Response(Status.INTERNAL_SERVER_ERROR)
        })
        assertFailsWith<IllegalStateException> { client.next() }
        assertEquals(2, calls)
    }
}
