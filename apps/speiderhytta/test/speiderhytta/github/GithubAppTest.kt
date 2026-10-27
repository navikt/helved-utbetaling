package speiderhytta.github

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import speiderhytta.GithubConfig
import java.net.URI
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GithubAppTest {
    @Test
    fun `ber om installation token med signert app JWT`() = runTest {
        val config = config(appId = "123")
        var capturedJwt: String? = null
        val http: HttpHandler = { request ->
            assertEquals(Method.POST, request.method)
            assertEquals("/app/installations/2/access_tokens", request.uri.path)
            assertEquals("application/vnd.github+json", request.header("Accept"))
            assertEquals("2022-11-28", request.header("X-GitHub-Api-Version"))
            capturedJwt = requireNotNull(request.header("Authorization")).removePrefix("Bearer ")
            jsonResponse("""{"token":"installation-token","expires_at":"2099-01-01T00:00:00Z"}""")
        }

        val before = Instant.now()
        val token = GithubApp(config, http).token()
        val after = Instant.now()

        assertEquals("installation-token", token)
        val parts = requireNotNull(capturedJwt).split(".")
        assertEquals(3, parts.size)
        val header = decodeJwtPart(parts[0])
        val payload = decodeJwtPart(parts[1])
        assertEquals("RS256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
        assertEquals("123", payload["iss"]?.jsonPrimitive?.content)
        val issuedAt = payload["iat"]!!.jsonPrimitive.content.toLong()
        val expiresAt = payload["exp"]!!.jsonPrimitive.content.toLong()
        assertTrue(issuedAt in before.epochSecond - 60..after.epochSecond)
        assertTrue(expiresAt > after.epochSecond)
        assertTrue(expiresAt <= after.epochSecond + 600)
    }

    @Test
    fun `gjenbruker installation token frem til utløp`() = runTest {
        var requests = 0
        val http: HttpHandler = {
            requests++
            jsonResponse("""{"token":"cached-token","expires_at":"2099-01-01T00:00:00Z"}""")
        }
        val app = GithubApp(config(), http)

        assertEquals("cached-token", app.token())
        assertEquals("cached-token", app.token())
        assertEquals(1, requests)
    }

    @Test
    fun `feiler uten å lekke privatnøkkel når tokenrequest avvises`() = runTest {
        val privateKey = privateKeyPem()
        val app = GithubApp(config(privateKeyPem = privateKey)) {
            jsonResponse("""{"message":"Bad credentials"}""", Status.UNAUTHORIZED)
        }

        val error = assertFailsWith<IllegalStateException> { app.token() }

        assertTrue(error.message.orEmpty().contains("HTTP 401"))
        assertTrue(!error.message.orEmpty().contains(privateKey))
    }

    private fun config(
        appId: String = "1",
        privateKeyPem: String = privateKeyPem(),
    ) = GithubConfig(
        apiUrl = URI("https://api.github.test").toURL(),
        appId = appId,
        installationId = "2",
        privateKeyPem = privateKeyPem,
    )

    private fun decodeJwtPart(encoded: String) =
        libs.kotlinx.KotlinxJson.parseToJsonElement(
            Base64.getUrlDecoder().decode(encoded).toString(Charsets.UTF_8),
        ).jsonObject

    private fun jsonResponse(body: String, status: Status = Status.OK): Response =
        Response(status).header("Content-Type", "application/json").body(body)

    private fun privateKeyPem(): String {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private.encoded
        return "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key)}\n-----END PRIVATE KEY-----"
    }
}
