package libs.auth

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import libs.cache.Cache
import libs.cache.TokenCache
import libs.http.HttpClientFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

class TokenClientTest {
    private val tokenStatus = AtomicReference(500)
    private val tokenRequests = AtomicInteger()
    private val server = embeddedServer(Netty, port = 0, module = { tokenServer(tokenStatus, tokenRequests) }).apply { start() }
    private val tokenUrl = URI("http://localhost:${server.engine.port}/token").toURL()

    @AfterEach
    fun tearDown() {
        server.stop(0, 0)
    }

    @Test
    fun `returns rejected and unavailable responses from token provider`() = runBlocking {
        val client = tokenClient()

        tokenStatus.set(400)
        assertEquals(ProviderRejected(400), client.getAccessToken(tokenUrl, "rejected", Parameters.Empty))

        tokenStatus.set(429)
        assertEquals(ProviderUnavailable(429), client.getAccessToken(tokenUrl, "rate-limited", Parameters.Empty))

        tokenStatus.set(500)
        assertEquals(ProviderUnavailable(500), client.getAccessToken(tokenUrl, "unavailable", Parameters.Empty))
    }

    @Test
    fun `returns unavailable when token provider cannot be reached`() = runBlocking {
        val client = tokenClient()
        val unavailableUrl = URI("http://localhost:1/token").toURL()

        assertEquals(ProviderUnavailable(), client.getAccessToken(unavailableUrl, "unavailable", Parameters.Empty))
    }

    @Test
    fun `caches OBO tokens separately for each assertion`() = runBlocking {
        tokenStatus.set(200)
        val azure = AzureTokenProvider(
            json = kotlinxJsonConfig,
            config = AzureConfig(
                tokenEndpoint = tokenUrl,
                jwks = tokenUrl,
                issuer = "test",
                clientId = "client",
                clientSecret = "secret",
            ),
            client = tokenClient(),
        )

        azure.getOnBehalfOfToken("first-assertion", "scope")
        azure.getOnBehalfOfToken("first-assertion", "scope")
        azure.getOnBehalfOfToken("second-assertion", "scope")

        assertEquals(2, tokenRequests.get())
    }

    @Test
    fun `caches username password tokens separately for each scope`() = runBlocking {
        tokenStatus.set(200)
        val azure = AzureTokenProvider(
            json = kotlinxJsonConfig,
            config = AzureConfig(
                tokenEndpoint = tokenUrl,
                jwks = tokenUrl,
                issuer = "test",
                clientId = "client",
                clientSecret = "secret",
            ),
            client = tokenClient(),
        )

        azure.getUsernamePasswordToken("first-scope", "username", "password")
        azure.getUsernamePasswordToken("first-scope", "username", "password")
        azure.getUsernamePasswordToken("second-scope", "username", "password")

        assertEquals(2, tokenRequests.get())
    }

    private fun tokenClient(cache: Cache<AzureToken> = TokenCache()): TokenClient =
        TokenClient(HttpClientFactory.new(kotlinxJsonConfig), "test", cache)
}

private fun Application.tokenServer(tokenStatus: AtomicReference<Int>, tokenRequests: AtomicInteger) {
    routing {
        post("/token") {
            tokenRequests.incrementAndGet()
            call.respondText(
                """{"expires_in":3600,"access_token":"token"}""",
                status = io.ktor.http.HttpStatusCode.fromValue(tokenStatus.get()),
                contentType = io.ktor.http.ContentType.Application.Json,
            )
        }
    }
}

private val ApplicationEngine.port: Int
    get() = runBlocking { resolvedConnectors().first().port }
