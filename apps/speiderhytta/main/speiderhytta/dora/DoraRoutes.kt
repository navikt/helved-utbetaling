package speiderhytta.dora

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import speiderhytta.HELVED_APPS
import speiderhytta.KotlinxJson
import java.time.Instant

/**
 * Read-only DORA API consumed by helved-peisen.
 *
 *   GET /dora                       → all apps, summary only
 *   GET /dora/{app}                 → single-app summary
 *   GET /dora/{app}/deployments     → recent deployments
 *   GET /dora/{app}/incidents       → recent incidents
 *
 * No auth on the route itself — gated upstream by NAIS access policy
 * (helved-peisen is the only allowed inbound).
 */
private val summaryListLens = KotlinxJson.autoBody<List<DoraSummary>>().toLens()
private val summaryLens = KotlinxJson.autoBody<DoraSummary>().toLens()
private val deploymentListLens = KotlinxJson.autoBody<List<Deployment>>().toLens()
private val incidentListLens = KotlinxJson.autoBody<List<Incident>>().toLens()

fun doraRoutes(
    query: DoraQueryService,
    jdbcCtx: CoroutineDatasource,
    apps: List<String> = HELVED_APPS,
): RoutingHttpHandler = routes(
    "/dora" bind Method.GET to {
        val summaries = runBlocking(jdbcCtx + Dispatchers.IO) { transaction { query.summaries(apps) } }
        Response(Status.OK).with(summaryListLens of summaries)
    },

    "/dora/{app}" bind Method.GET to { req ->
        val app = req.path("app")
        when {
            app.isNullOrBlank() -> Response(Status.BAD_REQUEST).body("missing app")
            app !in apps -> Response(Status.NOT_FOUND).body("unknown app")
            else -> {
                val summary = runBlocking(jdbcCtx + Dispatchers.IO) { transaction { query.summary(app) } }
                Response(Status.OK).with(summaryLens of summary)
            }
        }
    },

    "/dora/{app}/deployments" bind Method.GET to { req ->
        val app = req.path("app")
        when {
            app.isNullOrBlank() -> Response(Status.BAD_REQUEST).body("missing app")
            app !in apps -> Response(Status.NOT_FOUND).body("unknown app")
            else -> {
                val since = sinceParam(req.query("since"))
                val limit = req.query("limit")?.toIntOrNull() ?: 100
                val rows = runBlocking(jdbcCtx + Dispatchers.IO) {
                    transaction { Deployment.selectSuccessfulFor(app, "prod-gcp", since, limit) }
                }
                Response(Status.OK).with(deploymentListLens of rows)
            }
        }
    },

    "/dora/{app}/incidents" bind Method.GET to { req ->
        val app = req.path("app")
        when {
            app.isNullOrBlank() -> Response(Status.BAD_REQUEST).body("missing app")
            app !in apps -> Response(Status.NOT_FOUND).body("unknown app")
            else -> {
                val since = sinceParam(req.query("since"))
                val limit = req.query("limit")?.toIntOrNull() ?: 100
                val rows = runBlocking(jdbcCtx + Dispatchers.IO) {
                    transaction { Incident.selectFor(app, since, limit) }
                }
                Response(Status.OK).with(incidentListLens of rows)
            }
        }
    },
)

private fun sinceParam(raw: String?): Instant {
    if (raw.isNullOrBlank()) return Instant.now().minusSeconds(60L * 60 * 24 * 30)
    return runCatching { Instant.parse(raw) }.getOrElse { Instant.now().minusSeconds(60L * 60 * 24 * 30) }
}
