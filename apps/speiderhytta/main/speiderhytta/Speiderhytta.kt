package speiderhytta

import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.context
import libs.utils.Log
import org.http4k.client.JavaHttpClient
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.core.Method
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import speiderhytta.dora.DeployService
import speiderhytta.dora.DoraQueryService
import speiderhytta.dora.IncidentService
import speiderhytta.dora.Poller
import speiderhytta.dora.asDeployFetcher
import speiderhytta.dora.asFetcher
import speiderhytta.dora.doraRoutes
import speiderhytta.github.GithubClient
import speiderhytta.audit.AuditService
import speiderhytta.audit.asAuditFetcher
import speiderhytta.audit.auditRoutes
import speiderhytta.audit.TaskCommentProjector
import speiderhytta.audit.asTaskCommentClient
import speiderhytta.slo.PrometheusClient
import speiderhytta.slo.SloDefinitionLoader
import speiderhytta.slo.SloService
import speiderhytta.slo.sloRoutes
import java.time.Duration
import java.net.http.HttpClient

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val app = speiderhytta()
    val server = try {
        app.handler
            .asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(25))))
            .start()
    } catch (t: Throwable) {
        app.close()
        throw t
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        app.use { server.stop() }
    })

    server.block()
}

class Speiderhytta(
    val handler: HttpHandler,
    private val closeables: List<AutoCloseable>,
) : AutoCloseable {
    override fun close() = closeables.forEach { it.close() }
}

fun speiderhytta(config: Config = Config()): Speiderhytta {
    val meters = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    LogbackMetrics().bindTo(meters)

    val jdbcDataSource = Jdbc.initialize(config.jdbc)
    val jdbcCtx = jdbcDataSource.context()
    val appJob = SupervisorJob()

    try {
        runBlocking(jdbcCtx) {
            Migrator(config.jdbc.migrations).migrate()
        }

        val metrics = Metrics(meters)
        val github = GithubClient(config.github, httpClient())
        val deployService = DeployService(github.asDeployFetcher(), metrics, codeRepos = config.github.codeRepos, jdbcCtx = jdbcCtx)
        val incidentService = IncidentService(github.asFetcher(), metrics, jdbcCtx)
        val taskComments = TaskCommentProjector(github.asTaskCommentClient(), jdbcCtx)
        val auditService = AuditService(
            github.asAuditFetcher(),
            config.github.codeRepos,
            jdbcCtx,
            config.github.issueRepo,
            taskComments,
        )
        val doraQuery = DoraQueryService()

        val sloDefs = SloDefinitionLoader(config.slo.definitionsDir).load()
        Log.info("loaded ${sloDefs.size} SLO definitions")
        val prom = PrometheusClient(config.prometheus, httpClient())
        val sloService = SloService(sloDefs, prom, metrics, jdbcCtx)

        val scope = CoroutineScope(appJob + Dispatchers.IO)
        Poller("deploy", config.pollIntervals.deploy, metrics, jdbcCtx) { since ->
            deployService.ingest(since)
        }.launchIn(scope)
        Poller("incident", config.pollIntervals.incident, metrics, jdbcCtx) { since ->
            incidentService.ingest(since)
        }.launchIn(scope)
        config.github.codeRepos.distinctBy { it.repo }.forEach { repository ->
            Poller("audit-commits:${repository.repo}", config.pollIntervals.audit, metrics, jdbcCtx) { since ->
                auditService.ingestCommits(repository.repo, since.minusSeconds(AUDIT_OVERLAP_SECONDS))
            }.launchIn(scope)
            repository.apps.forEach { (app, workflowFile) ->
                Poller(
                    name = "audit-workflow:${repository.repo}:$app:$workflowFile",
                    interval = config.pollIntervals.audit,
                    metrics = metrics,
                    jdbcCtx = jdbcCtx,
                    allowCursorRewind = true,
                ) { since ->
                    auditService.ingestWorkflow(repository.repo, app, workflowFile, since.minusSeconds(AUDIT_WORKFLOW_LOOKBACK_SECONDS))
                }.launchIn(scope)
            }
        }
        scope.launch {
            while (true) {
                try {
                    auditService.snapshotControls()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.warn("GitHub control snapshot failed", t)
                }
                delay(config.pollIntervals.auditControls)
            }
        }
        scope.launch {
            while (true) {
                try {
                    sloService.snapshot()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.warn("SLO snapshot failed", t)
                }
                delay(config.pollIntervals.sloSnapshot)
            }
        }

        val handler = errorFilter
            .then(ServerFilters.MicrometerMetrics.RequestTimer(meters))
            .then(ServerFilters.MicrometerMetrics.RequestCounter(meters))
            .then(
                routes(
                    doraRoutes(doraQuery, jdbcCtx, config.apps),
                    sloRoutes(sloService, jdbcCtx, config.apps),
                    auditRoutes(
                        jdbcCtx = jdbcCtx,
                        codeRepositories = config.github.codeRepos.map { it.repo }.toSet(),
                    ),
                    actuatorRoutes(meters),
                ),
            )

        val closeables = listOf(
            AutoCloseable { runBlocking { appJob.cancelAndJoin() } },
            AutoCloseable { (jdbcDataSource as? AutoCloseable)?.close() },
        )

        return Speiderhytta(handler, closeables)
    } catch (t: Throwable) {
        runBlocking { appJob.cancelAndJoin() }
        (jdbcDataSource as? AutoCloseable)?.close()
        throw t
    }
}

private fun actuatorRoutes(meters: PrometheusMeterRegistry) = routes(
    "/actuator/metric" bind Method.GET to { Response(Status.OK).body(meters.scrape()) },
    "/actuator/health" bind Method.GET to { Response(Status.OK) },
)

private fun httpClient(): HttpHandler {
    val client = HttpClient.newBuilder()
        .connectTimeout(HTTP_CONNECT_TIMEOUT)
        .build()
    return JavaHttpClient(client, requestModifier = { it.timeout(HTTP_REQUEST_TIMEOUT) })
}

private val errorFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: Exception) {
            val msg = "Uhåndtert feil i ${request.method} ${request.uri.path} - Helved har fått beskjed."
            Log.error(msg, e)
            Response(Status.INTERNAL_SERVER_ERROR).body("Uhåndtert feil - Helved har fått beskjed.")
        }
    }
}

private const val AUDIT_OVERLAP_SECONDS = 600L
private const val AUDIT_WORKFLOW_LOOKBACK_SECONDS = 3600L
private val HTTP_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(30)
private val HTTP_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)
