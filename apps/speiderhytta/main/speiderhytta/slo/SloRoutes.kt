package speiderhytta.slo

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import speiderhytta.HELVED_APPS
import java.time.Instant

/**
 *   GET /slo                       → live status, all SLOs
 *   GET /slo/{app}                 → live status, single app
 *   GET /slo/{app}/{slo_name}      → live status, single named SLO
 *   GET /slo/{app}/history         → snapshot history for trend charts
 */
fun Route.sloRoutes(service: SloService, apps: List<String> = HELVED_APPS) {
    route("/slo") {
        get { call.respond(service.statusAll()) }

        route("/{app}") {
            get {
                val app = call.parameters["app"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing app")
                if (app !in apps) return@get call.respond(HttpStatusCode.NotFound, "unknown app")
                call.respond(service.status(app))
            }

            get("/history") {
                val app = call.parameters["app"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing app")
                if (app !in apps) return@get call.respond(HttpStatusCode.NotFound, "unknown app")
                val since = call.request.queryParameters["since"]
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: Instant.now().minusSeconds(60L * 60 * 24 * 7)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000
                val rows = withContext(Jdbc.context + Dispatchers.IO) {
                    transaction { SloSnapshot.selectFor(app, since, limit) }
                }
                call.respond(rows)
            }

            get("/{slo_name}") {
                val app = call.parameters["app"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing app")
                if (app !in apps) return@get call.respond(HttpStatusCode.NotFound, "unknown app")
                val sloName = call.parameters["slo_name"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "missing slo_name")
                val match = service.status(app).firstOrNull { it.name == sloName }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "unknown slo")
                call.respond(match)
            }
        }
    }
}
