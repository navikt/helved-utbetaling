package speiderhytta.github

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
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
            when (request.uri.path) {
                "/repos/navikt/repo/commits" -> {
                    requestedPages.add(request.query("page"))
                    val page = request.query("page")?.toInt()
                    val commits = if (page == 1) (1..100).map { commitJson("sha-$it") } else listOf(commitJson("sha-101"))
                    jsonResponse(commits.joinToString(prefix = "[", postfix = "]"))
                }
                else -> error("Unexpected request ${request.uri}")
            }
        }

        val commits = client.auditCommits("navikt/repo", "main", java.time.Instant.EPOCH)

        assertEquals(101, commits.size)
        assertEquals(listOf<String?>("1", "2"), requestedPages)
    }

    @Test
    fun `rulesets kombinerer liste og detaljer`() = runTest {
        val client = githubClient { request ->
            when (request.uri.path) {
                "/repos/navikt/repo/rulesets" -> {
                    assertEquals("true", request.query("includes_parents"))
                    jsonResponse("""[{"id":7,"_links":{"self":{"href":"https://api.github.test/repos/navikt/repo/rulesets/7"}}}]""")
                }
                "/repos/navikt/repo/rulesets/7" -> jsonResponse("""{"id":7,"name":"main protection"}""")
                else -> error("Unexpected request ${request.uri}")
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
            Status.NOT_FOUND to FetchStatus.NOT_FOUND,
            Status.FORBIDDEN to FetchStatus.FORBIDDEN,
            Status.INTERNAL_SERVER_ERROR to FetchStatus.FAILED,
        )

        statuses.forEach { (httpStatus, expected) ->
            val client = githubClient { request ->
                when (request.uri.path) {
                    "/repos/navikt/repo/branches/main/protection" -> jsonResponse("{}", httpStatus)
                    else -> error("Unexpected request ${request.uri}")
                }
            }

            assertEquals(expected, client.branchProtection("navikt/repo", "main").status)
        }
    }

    @Test
    fun `returnerer null når kommentaren som skal oppdateres er slettet`() = runTest {
        val client = githubClient { request ->
            when (request.uri.path) {
                "/repos/navikt/team-helved/issues/comments/42" -> jsonResponse("{}", Status.NOT_FOUND)
                else -> error("Unexpected request ${request.uri}")
            }
        }

        assertEquals(null, client.updateIssueComment("navikt/team-helved", 42, "oppdatert"))
    }

    @Test
    fun `propagerer cancellation ved kontrollhenting`() = runTest {
        val client = githubClient { request ->
            when (request.uri.path) {
                "/repos/navikt/repo/branches/main/protection" -> throw CancellationException("stopp")
                else -> error("Unexpected request ${request.uri}")
            }
        }

        assertFailsWith<CancellationException> { client.branchProtection("navikt/repo", "main") }
    }

    @Test
    fun `sender installation token og GitHub-headere på API-kall`() = runTest {
        val client = githubClient { request ->
            assertEquals("Bearer installation-token", request.header("Authorization"))
            assertEquals("application/vnd.github+json", request.header("Accept"))
            assertEquals("2022-11-28", request.header("X-GitHub-Api-Version"))
            jsonResponse("[]")
        }

        client.auditCommits("navikt/repo", "main", java.time.Instant.EPOCH)
    }

    @Test
    fun `oppretter issue-kommentar med riktig metode og JSON-body`() = runTest {
        val client = githubClient { request ->
            assertEquals(Method.POST, request.method)
            assertEquals("/repos/navikt/team-helved/issues/42/comments", request.uri.path)
            assertEquals("application/json", request.header("Content-Type"))
            assertEquals("""{"body":"kontrollert"}""", request.bodyString())
            jsonResponse("""{"id":7,"body":"kontrollert"}""")
        }

        val comment = client.createIssueComment("navikt/team-helved", 42, "kontrollert")

        assertEquals(7, comment.id)
        assertEquals("kontrollert", comment.body)
    }

    @Test
    fun `propagerer serverfeil ved opprettelse av issue-kommentar`() = runTest {
        val client = githubClient { jsonResponse("{}", Status.INTERNAL_SERVER_ERROR) }

        val error = assertFailsWith<GithubHttpException> {
            client.createIssueComment("navikt/team-helved", 42, "kontrollert")
        }

        assertEquals(Status.INTERNAL_SERVER_ERROR, error.status)
    }

    @Test
    fun `beholder tom liste som fallback ved feil under workflow-henting`() = runTest {
        val client = githubClient { error("GitHub unavailable") }

        assertEquals(emptyList(), client.appWorkflowRuns("navikt/repo", "deploy.yml", java.time.Instant.EPOCH))
    }

    @Test
    fun `beholder tom liste som fallback ved feil under jobbhenting`() = runTest {
        val client = githubClient { error("GitHub unavailable") }

        assertEquals(emptyList(), client.workflowRunJobs("navikt/repo", 42))
    }

    @Test
    fun `workflow-henting bruker eksisterende enkeltkall med hundre runs`() = runTest {
        var requests = 0
        val client = githubClient { request ->
            requests++
            assertEquals("/repos/navikt/repo/actions/workflows/deploy.yml/runs", request.uri.path)
            assertEquals("main", request.query("branch"))
            assertEquals(">=${java.time.Instant.EPOCH}", request.query("created"))
            assertEquals("100", request.query("per_page"))
            val runs = (1..100).map { workflowRunJson(it.toLong()) }
            jsonResponse(runs.joinToString(prefix = "{\"workflow_runs\":[", postfix = "]}"))
        }

        val runs = client.appWorkflowRuns("navikt/repo", "deploy.yml", java.time.Instant.EPOCH)

        assertEquals(100, runs.size)
        assertEquals(1, requests)
    }

    private fun githubClient(handler: (Request) -> Response): GithubClient {
        val config = GithubConfig(
            apiUrl = URI("https://api.github.test").toURL(),
            appId = "1",
            installationId = "2",
            privateKeyPem = privateKeyPem(),
        )
        val http: HttpHandler = { request ->
            if (request.uri.path == "/app/installations/2/access_tokens") {
                jsonResponse("""{"token":"installation-token","expires_at":"2099-01-01T00:00:00Z"}""")
            } else {
                handler(request)
            }
        }
        return GithubClient(config, http, GithubApp(config, http))
    }

    private fun jsonResponse(body: String, status: Status = Status.OK): Response =
        Response(status).header("Content-Type", "application/json").body(body)

    private fun commitJson(sha: String) = """{"sha":"$sha","commit":{"author":{"date":"2026-09-01T00:00:00Z"}}}"""

    private fun workflowRunJson(id: Long) = """
        {
          "id":$id,
          "name":"deploy",
          "head_sha":"sha-$id",
          "head_branch":"main",
          "event":"push",
          "status":"completed",
          "conclusion":"success",
          "created_at":"2026-09-01T00:00:00Z",
          "updated_at":"2026-09-01T00:01:00Z",
          "html_url":"https://github.test/runs/$id"
        }
    """.trimIndent()

    private fun privateKeyPem(): String {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private.encoded
        return "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key)}\n-----END PRIVATE KEY-----"
    }
}
