package speiderhytta.dora

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import speiderhytta.HELVED_APPS
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
fun Route.doraRoutes(query: DoraQueryService, jdbcCtx: CoroutineDatasource, apps: List<String> = HELVED_APPS) {
    route("/dora") {
        get {
            val summaries = withContext(jdbcCtx + Dispatchers.IO) {
                transaction { query.summaries(apps) }
            }
            call.respond(summaries)
        }

        route("/{app}") {
            get {
                val app = call.parameters["app"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing app")
                if (app !in apps) return@get call.respond(HttpStatusCode.NotFound, "unknown app")
                val summary = withContext(jdbcCtx + Dispatchers.IO) {
                    transaction { query.summary(app) }
                }
                call.respond(summary)
            }

            get("/deployments") {
                val app = call.parameters["app"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing app")
                if (app !in apps) return@get call.respond(HttpStatusCode.NotFound, "unknown app")
                val since = sinceParam(call.request.queryParameters["since"])
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val rows = withContext(jdbcCtx + Dispatchers.IO) {
                    transaction { Deployment.selectSuccessfulFor(app, "prod-gcp", since, limit) }
                }
                call.respond(rows)
            }

            get("/incidents") {
                val app = call.parameters["app"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing app")
                if (app !in apps) return@get call.respond(HttpStatusCode.NotFound, "unknown app")
                val since = sinceParam(call.request.queryParameters["since"])
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val rows = withContext(jdbcCtx + Dispatchers.IO) {
                    transaction { Incident.selectFor(app, since, limit) }
                }
                call.respond(rows)
            }
        }
    }
}

private fun sinceParam(raw: String?): Instant {
    if (raw.isNullOrBlank()) return Instant.now().minusSeconds(60L * 60 * 24 * 30)
    return runCatching { Instant.parse(raw) }.getOrElse { Instant.now().minusSeconds(60L * 60 * 24 * 30) }
}
