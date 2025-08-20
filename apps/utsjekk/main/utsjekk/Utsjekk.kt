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
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.*
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.jdbc.*
import libs.utils.*
import models.kontrakter.felles.Fagsystem
import utsjekk.clients.SimuleringClient
import utsjekk.iverksetting.IverksettingService
import utsjekk.iverksetting.iverksetting
import utsjekk.simulering.SimuleringValidator
import utsjekk.simulering.simulerBlocking
import utsjekk.simulering.simulering
import utsjekk.utbetaling.UtbetalingService
import utsjekk.utbetaling.simulering.SimuleringService
import utsjekk.utbetaling.utbetalingRoute

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
        module = Application::utsjekk,
    ).start(wait = true)
}

fun Application.utsjekk(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
) {

    val abetalClient = AbetalClient(config)

    val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = metrics
        meterBinders += LogbackMetrics()
    }

    kafka.connect(
        createTopology(abetalClient),
        config.kafka,
        metrics,
    )

    val oppdragProducer = kafka.createProducer(config.kafka, Topics.oppdrag)
    val dpUtbetalingerProducer = kafka.createProducer(config.kafka, Topics.utbetalingDp)

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
        oppdragProducer.close()
        dpUtbetalingerProducer.close()
    }

    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(File("migrations")).migrate()
        }
    }

    val utbetalingService = UtbetalingService(oppdragProducer)
    val iverksettingService = IverksettingService(oppdragProducer)

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
                is ApiError -> call.respond(HttpStatusCode.fromValue(cause.statusCode), cause.asResponse)
                is BadRequestException -> {
                    val msg =
                        "Klarte ikke lese json meldingen. Sjekk at formatet på meldingen din er korrekt, f.eks navn på felter, påkrevde felter, e.l."
                    appLog.debug(msg) // client error
                    secureLog.debug(msg, cause) // client error
                    val res = ApiError.Response(msg = msg, field = null, doc = DEFAULT_DOC_STR)
                    call.respond(HttpStatusCode.BadRequest, res)
                }

                else -> {
                    val msg = "Intern feil, årsaken logges av sikkerhetsmessig grunn i secureLog."
                    appLog.error(msg)
                    secureLog.error(msg, cause)
                    val res = ApiError.Response(msg = msg, field = null, doc = "")
                    call.respond(HttpStatusCode.InternalServerError, res)
                }
            }
        }
    }

    val simulering = SimuleringClient(config)
    val simuleringService = SimuleringService(simulering, abetalClient)
    val simuleringValidator = SimuleringValidator(iverksettingService)

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksetting(iverksettingService)
            simulering(simuleringValidator, simulering)
            simulerBlocking(dpUtbetalingerProducer)
            utbetalingRoute(simuleringService, utbetalingService)
        }

        probes(metrics)
    }
}

fun ApplicationCall.client(): Client =
    principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        ?.split(":")
        ?.last()
        ?.let(::Client)
        ?: forbidden("missing JWT claim", "azp_name", "kom_i_gang")

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
