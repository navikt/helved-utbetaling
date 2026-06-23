package peisschtappern

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.JwtPrincipal
import libs.auth.TokenProvider
import libs.auth.Jwt
import libs.auth.jwt
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.context
import libs.kafka.*
import libs.utils.appLog
import libs.utils.secureLog
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(
        factory = Netty,
        configure = {
            shutdownGracePeriod = 5000L
            shutdownTimeout = 50_000L
            connectors.add(EngineConnectorBuilder().apply {
                port = 8080
            })
        },
        module = Application::peisschtappern,
    ).start(wait = true)
}

fun Application.peisschtappern(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(Authentication) {
        jwt(TokenProvider.AZURE, config.azure)
    }

    install(ContentNegotiation) {
        json(libs.kotlinx.KotlinxJson)
    }

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ApiError -> call.respond(HttpStatusCode.fromValue(cause.statusCode), cause)
                is BadRequestException -> {
                    val msg = "Klarte ikke lese json meldingen. Sjekk at formatet på meldingen din er korrekt, f.eks navn på felter, påkrevde felter, e.l."
                    appLog.debug(msg)
                    secureLog.debug(msg, cause)
                    val res = ApiError(statusCode = 400, msg = msg)
                    call.respond(HttpStatusCode.BadRequest, res)
                }
                else -> {
                    val msg = "Ukjent feil, helved er varslet."
                    appLog.error(msg, cause)
                    val res = ApiError(statusCode = 500, msg = msg)
                    call.respond(HttpStatusCode.InternalServerError, res)
                }
            }
        }
    }

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
    val manuellEndringService = ManuellEndringService(oppdragsdataProducer, utbetalingProducer, dpProducer, tsProducer, statusProducer)

    routing {
        probes(kafka, prometheus)

        authenticate(TokenProvider.AZURE) {
            api(manuellEndringService, jdbcCtx)
        }
    }

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
        oppdragsdataProducer.close()
    }

}

data class Audit(
    val ident: String,
    val name: String,
    val email: String,
    val reason: String?,
) {
    companion object {
        fun from(call: ApplicationCall, reason: String? = null): Audit {
            val claims = call.principal<JwtPrincipal>()?.claims ?: Jwt.Claims(emptyMap())
            return Audit(
                name = claims.claim("name") ?: "test",
                ident = claims.claim("NAVident") ?: "test",
                email = claims.claim("preferred_username") ?: "test",
                reason = reason
            )
        }
    }

    override fun toString(): String =
        if (reason != null) {
            "name:$name email:$email ident:$ident reason:$reason"
        } else {
            "name:$name email:$email ident:$ident"
        }
}

