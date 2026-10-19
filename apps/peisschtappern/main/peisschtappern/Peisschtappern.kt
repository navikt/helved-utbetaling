package peisschtappern

import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.JwksClient
import libs.auth.JwtVerifier
import libs.jdbc.*
import libs.jdbc.concurrency.CoroutineDatasource
import libs.kafka.*
import libs.utils.Log
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.http4k.core.HttpHandler
import org.http4k.core.then
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.routing.routes
import org.http4k.server.*
import java.net.http.HttpClient
import java.time.Duration

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val app = peisschtappern()
    val server = app.handler
        .asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50))))
        .start()

    Runtime.getRuntime().addShutdownHook(Thread {
        app.use { server.stop() }
    })

    server.block()
}

class Peisschtappern(val handler: HttpHandler, private val closeables: List<AutoCloseable>) : AutoCloseable {
    override fun close() = closeables.forEach { it.close() }
}

fun peisschtappern(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    auditLogs: AuditLogReader = GcpAuditLogReader()
): Peisschtappern {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val logbackMetrics = LogbackMetrics().also { it.bindTo(prometheus) }
    val jdbcCtx: CoroutineDatasource = Jdbc.initialize(config.jdbc).context()

    runBlocking {
        withContext(jdbcCtx) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    kafka.connect(
        topology = createTopology(config, jdbcCtx),
        config = config.kafka,
        registry = prometheus
    )

    val oppdragsdataProducer = kafka.createProducer(config.kafka, Topic("helved.oppdrag.v1", xml<Oppdrag>()))
    val utbetalingProducer = kafka.createProducer(config.kafka, Topic("helved.utbetalinger.v1", json<Utbetaling>()))
    val dpProducer = kafka.createProducer(config.kafka, Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>()))
    val tsProducer = kafka.createProducer(config.kafka, Topic("helved.utbetalinger-ts.v1", json<TsDto>()))
    val statusProducer = kafka.createProducer(config.kafka, Topic("helved.status.v1", json<StatusReply>()))

    val manuellEndringService = ManuellEndringService(
        oppdragProducer = oppdragsdataProducer,
        utbetalingerProducer = utbetalingProducer,
        dpProducer = dpProducer,
        tsProducer = tsProducer,
        statusProducer = statusProducer
    )

    val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
    val verifier = JwtVerifier(JwksClient(config.azure.jwks, http), config.azure)
    val protectedRoutes = azureAuthFilter(verifier).then(
        routes(
            *listOfNotNull(
                api(manuellEndringService, jdbcCtx),
                auditLogs(auditLogs, config.auditLogBucket, config.databaseAuditLog)
            ).toTypedArray()
        )
    )

    val handler = ServerFilters.MicrometerMetrics.RequestTimer(prometheus)
        .then(ServerFilters.MicrometerMetrics.RequestCounter(prometheus))
        .then(errorFilter)
        .then(routes(probes(kafka, prometheus), protectedRoutes))

    return Peisschtappern(
        handler,
        listOfNotNull(
            kafka,
            oppdragsdataProducer,
            utbetalingProducer,
            dpProducer,
            tsProducer,
            statusProducer,
            auditLogs as? AutoCloseable,
            jdbcCtx.datasource as? AutoCloseable,
            http,
            logbackMetrics,
            AutoCloseable { prometheus.close() }
        )
    )
}
