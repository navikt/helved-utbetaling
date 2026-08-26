package utsjekk.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.jdbc.concurrency.CoroutineDatasource
import models.unauthorized
import utsjekk.*
import utsjekk.iverksetting.IverksettingService
import utsjekk.simulering.*

class SimuleringRoutes(
    private val config: Config,
    iverksettingService: IverksettingService,
    private val jdbcCtx: CoroutineDatasource,
    private val client: HttpClient = HttpClientFactory.new(
        json = libs.kotlinx.KotlinxJson,
        retries = 1,
        requestTimeoutMs = 130_000, // slightly above simulering's 120s dryrun timeout
        connectionTimeoutMs = 5000,
    ),
    private val azure: AzureTokenProvider = AzureTokenProvider(libs.kotlinx.KotlinxJson, config.azure),
) {
    private val validatorV2: SimuleringService = SimuleringService(iverksettingService, jdbcCtx)
    private val simuleringClient = SimuleringClient(config, jdbcCtx)

    fun utsjekk(route: Route) {
        // Tiltakspenger er de eneste som bruker denne
        route.route("/api/simulering/v2") {
            post {
                val fagsystem = call.fagsystem()
                val dto = call.receive<api.SimuleringRequest>()
                val simulering = domain.Simulering.from(dto, fagsystem)
                validatorV2.valider(simulering)

                val token = if (call.hasClaim("NAVident")) {
                    TokenType.Obo(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
                } else if (call.hasClaim("azp_name")) {
                    TokenType.Client(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
                } else {
                    unauthorized("Mangler claims")
                }

                when (val res = simuleringClient.hentSimuleringsresultatMedOppsummering(simulering, token)) {
                    null -> call.respond(HttpStatusCode.NoContent)
                    else -> call.respond(res)
                }
            }
        }
    }

    // Proxy /api/simulering/v3 to apps/simulering.
    // Kept for backwards compatibility — callers should migrate to calling simulering directly.
    fun abetal(route: Route) {
        route.route("/api/simulering/v3") {
            post {
                proxyToSimulering(call, "/api/simulering")
            }
        }
    }

    // Proxy /api/dryrun/* to apps/simulering.
    // Kept for backwards compatibility — callers should migrate to calling simulering directly.
    fun dryrun(route: Route) {
        route.route("/api/dryrun") {
            post("/aap") { proxyToSimulering(call, "/api/simulering") }
            post("/dagpenger") { proxyToSimulering(call, "/api/simulering") }
            post("/tilleggsstonader") { proxyToSimulering(call, "/api/simulering") }
            post("/tiltakspenger") { proxyToSimulering(call, "/api/simulering") }
        }
    }

    private suspend fun proxyToSimulering(call: RoutingCall, path: String) {
        val token = azure.getClientCredentialsToken(config.simulering.scope)
        val body = call.receive<ByteArray>()

        val response = client.post("${config.simulering.host}$path") {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            call.request.headers["Transaction-ID"]?.let { header("Transaction-ID", it) }
            call.request.headers["fagsystem"]?.let { header("fagsystem", it) }
            setBody(body)
        }

        val responseBody = response.bodyAsText()
        val contentType = response.contentType() ?: ContentType.Application.Json
        call.respondText(responseBody, contentType, response.status)
    }
}
