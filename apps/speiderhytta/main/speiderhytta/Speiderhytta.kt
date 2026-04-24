package speiderhytta

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.http.HttpClientFactory
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.context
import libs.jdbc.concurrency.CoroutineDatasource
import libs.utils.appLog
import libs.utils.secureLog
import speiderhytta.dora.DeployService
import speiderhytta.dora.DoraQueryService
import speiderhytta.dora.IncidentService
import speiderhytta.dora.Poller
import speiderhytta.dora.asDeployFetcher
import speiderhytta.dora.asFetcher
import speiderhytta.dora.doraRoutes
import speiderhytta.github.GithubApp
import speiderhytta.github.GithubClient
import speiderhytta.slo.PrometheusClient
import speiderhytta.slo.SloDefinitionLoader
import speiderhytta.slo.SloService
import speiderhytta.slo.sloRoutes

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(
        factory = Netty,
        configure = {
            shutdownGracePeriod = 5_000L
            shutdownTimeout = 50_000L
            connectors.add(EngineConnectorBuilder().apply { port = 8080 })
        },
        module = Application::speiderhytta,
    ).start(wait = true)
}

fun Application.speiderhytta(config: Config = Config()) {
    val meters = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = meters
        meterBinders += LogbackMetrics()
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    val jdbcCtx: CoroutineDatasource = Jdbc.initialize(config.jdbc).context()
    runBlocking {
        withContext(jdbcCtx) { Migrator(config.jdbc.migrations).migrate() }
    }

    val metrics = Metrics(meters)
    val httpClient = HttpClientFactory.new()
    val githubApp = GithubApp(config.github, httpClient)
    val github = GithubClient(config.github, app = githubApp)
    val deployService = DeployService(github.asDeployFetcher(), metrics, codeRepos = config.github.codeRepos, jdbcCtx = jdbcCtx)
    val incidentService = IncidentService(github.asFetcher(), metrics, jdbcCtx)
    val doraQuery = DoraQueryService()

    val sloDefs = SloDefinitionLoader(config.slo.definitionsDir).load()
    appLog.info("loaded {} SLO definitions", sloDefs.size)
    val prom = PrometheusClient(config.prometheus)
    val sloService = SloService(sloDefs, prom, metrics, jdbcCtx)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    Poller("deploy", config.pollIntervals.deploy, metrics, jdbcCtx) { since ->
        deployService.ingest(since)
    }.launchIn(scope)
    Poller("incident", config.pollIntervals.incident, metrics, jdbcCtx) { since ->
        incidentService.ingest(since)
    }.launchIn(scope)
    scope.launch {
        while (true) {
            try {
                sloService.snapshot()
            } catch (t: Throwable) {
                appLog.warn("SLO snapshot failed", t)
            }
            delay(config.pollIntervals.sloSnapshot)
        }
    }

    routing {
        doraRoutes(doraQuery, jdbcCtx, config.apps)
        sloRoutes(sloService, jdbcCtx, config.apps)
        route("/actuator") {
            get("/metric") { call.respond(meters.scrape()) }
            get("/health") { call.respond(HttpStatusCode.OK) }
        }
    }
}
