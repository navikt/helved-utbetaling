package speiderhytta.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import speiderhytta.GithubConfig
import java.net.URI
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GithubClientTest {
    @Test
    fun `paginering henter alle commits med riktige parametere`() = runTest {
        val requestedPages = mutableListOf<String?>()
        val client = githubClient { request ->
            when (request.url.encodedPath) {
                "/repos/navikt/repo/commits" -> {
                    requestedPages.add(request.url.parameters["page"])
                    val page = request.url.parameters["page"]?.toInt()
                    val commits = if (page == 1) (1..100).map { commitJson("sha-$it") } else listOf(commitJson("sha-101"))
                    jsonResponse(commits.joinToString(prefix = "[", postfix = "]"))
                }
                else -> error("Unexpected request ${request.url}")
            }
        }

        val commits = client.auditCommits("navikt/repo", "main", java.time.Instant.EPOCH)

        assertEquals(101, commits.size)
        assertEquals(listOf<String?>("1", "2"), requestedPages)
    }

    @Test
    fun `rulesets kombinerer liste og detaljer`() = runTest {
        val client = githubClient { request ->
            when (request.url.encodedPath) {
                "/repos/navikt/repo/rulesets" -> {
                    assertEquals("true", request.url.parameters["includes_parents"])
                    jsonResponse("""[{"id":7,"_links":{"self":{"href":"https://api.github.test/repos/navikt/repo/rulesets/7"}}}]""")
                }
                "/repos/navikt/repo/rulesets/7" -> jsonResponse("""{"id":7,"name":"main protection"}""")
                else -> error("Unexpected request ${request.url}")
            }
        }

        val control = client.repositoryRulesets("navikt/repo")

        assertEquals(FetchStatus.PRESENT, control.status)
        assertEquals(7, control.payload.jsonObject["summaries"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("main protection", control.payload.jsonObject["details"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `mapper GitHub-feil til fetch status`() = runTest {
        val statuses = listOf(
            HttpStatusCode.NotFound to FetchStatus.NOT_FOUND,
            HttpStatusCode.Forbidden to FetchStatus.FORBIDDEN,
            HttpStatusCode.InternalServerError to FetchStatus.FAILED,
        )

        statuses.forEach { (httpStatus, expected) ->
            val client = githubClient { request ->
                when (request.url.encodedPath) {
                    "/repos/navikt/repo/branches/main/protection" -> jsonResponse("{}", httpStatus)
                    else -> error("Unexpected request ${request.url}")
                }
            }

            assertEquals(expected, client.branchProtection("navikt/repo", "main").status)
        }
    }

    @Test
    fun `returnerer null når kommentaren som skal oppdateres er slettet`() = runTest {
        val client = githubClient { request ->
            when (request.url.encodedPath) {
                "/repos/navikt/team-helved/issues/comments/42" -> jsonResponse("{}", HttpStatusCode.NotFound)
                else -> error("Unexpected request ${request.url}")
            }
        }

        assertEquals(null, client.updateIssueComment("navikt/team-helved", 42, "oppdatert"))
    }

    @Test
    fun `propagerer cancellation ved kontrollhenting`() = runTest {
        val client = githubClient { request ->
            when (request.url.encodedPath) {
                "/repos/navikt/repo/branches/main/protection" -> throw CancellationException("stopp")
                else -> error("Unexpected request ${request.url}")
            }
        }

        assertFailsWith<CancellationException> { client.branchProtection("navikt/repo", "main") }
    }

    private fun githubClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): GithubClient {
        val config = GithubConfig(
            apiUrl = URI("https://api.github.test").toURL(),
            appId = "1",
            installationId = "2",
            privateKeyPem = privateKeyPem(),
        )
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/app/installations/2/access_tokens") {
                jsonResponse("""{"token":"installation-token","expires_at":"2099-01-01T00:00:00Z"}""")
            } else {
                handler(request)
            }
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(libs.kotlinx.KotlinxJson) }
        }
        return GithubClient(config, http, GithubApp(config, http))
    }

    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun commitJson(sha: String) = """{"sha":"$sha","commit":{"author":{"date":"2026-09-01T00:00:00Z"}}}"""

    private fun privateKeyPem(): String {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private.encoded
        return "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key)}\n-----END PRIVATE KEY-----"
    }
}
