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
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.postgres.Jdbc
import libs.postgres.JdbcConfig
import libs.postgres.Migrator
import libs.utils.logger
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import utsjekk.clients.SimuleringClient
import utsjekk.iverksetting.iverksetting
import utsjekk.iverksetting.IverksettingService
import utsjekk.simulering.SimuleringValidator
import utsjekk.simulering.simulering
import utsjekk.task.tasks
import utsjekk.utbetaling.UtbetalingService
import utsjekk.utbetaling.simulering.SimuleringService
import utsjekk.utbetaling.utbetalingRoute
import java.io.File

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }
    embeddedServer(Netty, port = 8080, module = Application::utsjekk).start(wait = true)
}

fun Application.utsjekk(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
) {
    val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = metrics
        meterBinders += LogbackMetrics()
    }

    kafka.connect(
        createTopology(),
        config.kafka,
        metrics,
    )

    val oppdragProducer = OppdragKafkaProducer(config.kafka, kafka)

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
        oppdragProducer.close()
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
    install(DoubleReceive)
    install(CallLog) {
        exclude { call -> call.request.path().startsWith("/probes") }
        log { call ->
            appLog.info("${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms")
            secureLog.info(
                """
                ${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms
                ${call.bodyAsText()}
                """.trimIndent(),
            )
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ApiError -> {
                    call.respond(HttpStatusCode.fromValue(cause.statusCode), cause.asResponse)
                }

                is BadRequestException -> {
                    val res = ApiError.Response(
                        msg = "Klarte ikke lese json meldingen. Sjekk at formatet på meldingen din er korrekt, f.eks navn på felter, påkrevde felter, e.l.",
                        field = null,
                        doc = DEFAULT_DOC_STR,
                    )
                    call.respond(HttpStatusCode.BadRequest, res)
                }

                else -> {
                    secureLog.error("Intern feil", cause)
                    val res = ApiError.Response(
                        msg = "Intern feil, årsaken logges av sikkerhetsmessig grunn i secureLog.", 
                        field = null,
                        doc = ""
                    )
                    call.respond(HttpStatusCode.InternalServerError, res)
                }
            }
        }
    }

    val simulering = SimuleringClient(config)
    val simuleringService = SimuleringService(simulering)
    val simuleringValidator = SimuleringValidator(iverksettingService)

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksetting(iverksettingService)
            simulering(simuleringValidator, simulering)
            utbetalingRoute(simuleringService, utbetalingService)
            tasks()
        }

        probes(metrics)
    }
}

fun ApplicationCall.navident(): String =
    principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
        ?: forbidden("missing JWT claim", "NAVident", "kom_i_gang")

fun ApplicationCall.client(): Client =
    principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        ?.split(":")
        ?.last()
        ?.let(::Client)
        ?: forbidden("missing JWT claim", "azp_name", "kom_i_gang")

@JvmInline
value class Client(
    private val name: String,
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
