package utsjekk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.kafka.Topology
import libs.ktor.CallLog
import libs.ktor.bodyAsText
import libs.utils.appLog
import libs.utils.secureLog
import models.ApiError
import models.badRequest
import models.forbidden
import models.kontrakter.Fagsystem
import models.unauthorized
import utsjekk.iverksetting.IverksettingMigrator
import utsjekk.iverksetting.IverksettingService
import utsjekk.routes.SimuleringRoutes
import utsjekk.routes.actuator
import utsjekk.routes.iverksetting
import utsjekk.routing.utbetalinger
import utsjekk.utbetaling.UtbetalingMigrator
import utsjekk.utbetaling.UtbetalingService
import java.io.File
import java.util.*

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
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
        module = Application::utsjekk,
    ).start(wait = true)
}

fun Application.utsjekk(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    topology: Topology = createTopology(),
) {
    val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = metrics
        meterBinders += LogbackMetrics()
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
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

    install(DoubleReceive)

    install(CallLog) {
        exclude { call -> call.request.path().startsWith("/actuator") }
        log { call ->
            appLog.info("${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()}")
            if (call.response.status()?.isSuccess() == false) {
                secureLog.info(
                    """${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()}
${call.bodyAsText()}""".trimIndent()
                )
            }
        }
    }

    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(File("migrations")).migrate()
        }
    }

    kafka.connect(
        topology,
        config.kafka,
        metrics,
    )

    val oppdragProducer = kafka.createProducer(config.kafka, Topics.oppdrag)
    val utbetalingProducer = kafka.createProducer(config.kafka, Topics.utbetaling)

    val utbetalingService = UtbetalingService(oppdragProducer)
    val iverksettingService = IverksettingService(oppdragProducer)

    val simuleringRoutes = SimuleringRoutes(
        config,
        kafka,

        iverksettingService,
        utbetalingService,
    )

    val utbetalingMigrator = UtbetalingMigrator(utbetalingProducer)
    val iverksettingMigrator = IverksettingMigrator(iverksettingService, utbetalingProducer)

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksetting(iverksettingService, iverksettingMigrator)
            utbetalinger(utbetalingService, utbetalingMigrator)
            simuleringRoutes.aap(this)
            simuleringRoutes.utsjekk(this)
            simuleringRoutes.abetal(this)
        }

        actuator(metrics)
    }

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
        oppdragProducer.close()
        utbetalingProducer.close()
        simuleringRoutes.close()
    }
}

fun ApplicationCall.client(): Client =
    principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        ?.split(":")
        ?.last()
        ?.let(::Client)
        ?: forbidden("missing JWT claim")

fun ApplicationCall.hasClaim(claim: String): Boolean =
    principal<JWTPrincipal>()?.getClaim(claim, String::class) != null

sealed class TokenType(open val jwt: String) {
    data class Obo(override val jwt: String) : TokenType(jwt)
    data class Client(override val jwt: String) : TokenType(jwt)
}

fun RoutingCall.fagsystem() =
    if (System.getenv("ENV") != "prod") {
        request.header("Fagsystem")?.let { Fagsystem.valueOf(it) }
            ?: client().toFagsystem()
    } else {
        client().toFagsystem()
    }

@JvmInline
value class Client(
    val name: String,
) {
    fun toFagsystem(): Fagsystem =
        when (name) {
            "azure-token-generator" -> Fagsystem.AAP
            "helved-performance" -> Fagsystem.DAGPENGER
            "tilleggsstonader-sak" -> Fagsystem.TILLEGGSSTØNADER
            "tiltakspenger-saksbehandling-api" -> Fagsystem.TILTAKSPENGER
            else ->
                forbidden(
                    msg = "mangler mapping mellom appname ($name) og fagsystem-enum",
                    doc = "kom_i_gang",
                )
        }
}

fun RoutingCall.getTokenType(): TokenType? {
    return if(hasClaim("NAVident")) {
        TokenType.Obo(request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing"))
    } else if (hasClaim("azp_name")) {
        TokenType.Client(request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing"))
    } else {
        unauthorized("Mangler claim, enten azp_name eller NAVident")
    }
}

fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch (e: Exception) {
        badRequest("Path param må være UUID")
    }
}
