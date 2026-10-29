package speiderhytta.slo

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
 *   GET /slo                       → live status, all SLOs
 *   GET /slo/{app}                 → live status, single app
 *   GET /slo/{app}/{slo_name}      → live status, single named SLO
 *   GET /slo/{app}/history         → snapshot history for trend charts
 */
private val statusListLens = KotlinxJson.autoBody<List<SloStatus>>().toLens()
private val statusLens = KotlinxJson.autoBody<SloStatus>().toLens()
private val snapshotListLens = KotlinxJson.autoBody<List<SloSnapshot>>().toLens()

fun sloRoutes(
    service: SloService,
    jdbcCtx: CoroutineDatasource,
    apps: List<String> = HELVED_APPS,
): RoutingHttpHandler = routes(
    "/slo" bind Method.GET to {
        Response(Status.OK).with(statusListLens of runBlocking { service.statusAll() })
    },

    "/slo/{app}" bind Method.GET to { req ->
        val app = req.path("app")
        when {
            app.isNullOrBlank() -> Response(Status.BAD_REQUEST).body("missing app")
            app !in apps -> Response(Status.NOT_FOUND).body("unknown app")
            else -> Response(Status.OK).with(statusListLens of runBlocking { service.status(app) })
        }
    },

    // Must precede the `{slo_name}` route so "history" is matched specifically.
    "/slo/{app}/history" bind Method.GET to { req ->
        val app = req.path("app")
        when {
            app.isNullOrBlank() -> Response(Status.BAD_REQUEST).body("missing app")
            app !in apps -> Response(Status.NOT_FOUND).body("unknown app")
            else -> {
                val since = req.query("since")
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: Instant.now().minusSeconds(60L * 60 * 24 * 7)
                val limit = req.query("limit")?.toIntOrNull() ?: 1000
                val rows = runBlocking(jdbcCtx + Dispatchers.IO) {
                    transaction { SloSnapshot.selectFor(app, since, limit) }
                }
                Response(Status.OK).with(snapshotListLens of rows)
            }
        }
    },

    "/slo/{app}/{slo_name}" bind Method.GET to { req ->
        val app = req.path("app")
        val sloName = req.path("slo_name")
        when {
            app.isNullOrBlank() -> Response(Status.BAD_REQUEST).body("missing app")
            app !in apps -> Response(Status.NOT_FOUND).body("unknown app")
            sloName.isNullOrBlank() -> Response(Status.BAD_REQUEST).body("missing slo_name")
            else -> {
                val match = runBlocking { service.status(app) }.firstOrNull { it.name == sloName }
                if (match == null) Response(Status.NOT_FOUND).body("unknown slo")
                else Response(Status.OK).with(statusLens of match)
            }
        }
    },
)
