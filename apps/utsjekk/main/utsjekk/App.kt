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
import libs.kafka.Kafka
import libs.postgres.Jdbc
import libs.postgres.JdbcConfig
import libs.postgres.Migrator
import libs.utils.logger
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import utsjekk.avstemming.AvstemmingTaskStrategy
import utsjekk.clients.OppdragClient
import utsjekk.clients.SimuleringClient
import utsjekk.iverksetting.IverksettingTaskStrategy
import utsjekk.iverksetting.Iverksettinger
import utsjekk.iverksetting.iverksetting
import utsjekk.simulering.SimuleringValidator
import utsjekk.simulering.simulering
import utsjekk.status.StatusKafkaProducer
import utsjekk.status.StatusTaskStrategy
import utsjekk.task.TaskScheduler
import utsjekk.task.tasks
import java.io.File

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080) {
        val config = Config()
        database(config.jdbc)
        server(config)
    }.start(wait = true)
}

fun database(config: JdbcConfig) {
    Jdbc.initialize(config)

    runBlocking {
        withContext(Jdbc.context) {
            Migrator(File("migrations")).migrate()
        }
    }
}

fun Application.server(
    config: Config = Config(),
    featureToggles: FeatureToggles = UnleashFeatureToggles(config.unleash),
    statusProducer: Kafka<StatusEndretMelding> = StatusKafkaProducer(config.kafka),
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

    install(DoubleReceive)

    install(CallLog) {
        exclude { call -> call.request.path().startsWith("/probes") }
        log { call ->
            appLog.info("${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms")
            secureLog.info(
                """
                ${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms
                ${call.bodyAsText()}
                """.trimIndent()
            )
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ApiError.BadRequest -> call.respond(HttpStatusCode.BadRequest, cause.message)
                is ApiError.NotFound -> call.respond(HttpStatusCode.NotFound, cause.message)
                is ApiError.Conflict -> call.respond(HttpStatusCode.Conflict, cause.message)
                is ApiError.Unauthorized -> call.respond(HttpStatusCode.Unauthorized, cause.message)
                is ApiError.Forbidden -> call.respond(HttpStatusCode.Forbidden, cause.message)
                is ApiError.Unavailable -> call.respond(HttpStatusCode.ServiceUnavailable, cause.message)
                else -> {
                    secureLog.error("Unknown error.", cause)
                    call.respond(HttpStatusCode.UnprocessableEntity, "Unknown error. See logs")
                }
            }
        }
    }

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    val oppdrag = OppdragClient(config)
    val simulering = SimuleringClient(config)
    val iverksettinger = Iverksettinger(featureToggles, statusProducer)
    val simuleringValidator = SimuleringValidator(iverksettinger)
    val scheduler =
        TaskScheduler(
            listOf(
                IverksettingTaskStrategy(oppdrag, iverksettinger),
                StatusTaskStrategy(oppdrag),
                AvstemmingTaskStrategy(oppdrag).apply {
                    runBlocking {
                        withContext(Jdbc.context) {
                            initiserAvstemmingForNyeFagsystemer()
                        }
                    }
                },
            ),
            LeaderElector(config),
            metrics
        )

    monitor.subscribe(ApplicationStopping) {
        scheduler.close()
        statusProducer.close()
    }

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksetting(iverksettinger)
            simulering(simuleringValidator, simulering)
            tasks()
        }

        probes(metrics)
    }
}

fun ApplicationCall.navident(): String =
    principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
        ?: ApiError.forbidden("missing claims: NAVident")

fun ApplicationCall.client(): Client =
    principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        ?.split(":")
        ?.last()
        ?.let(::Client)
        ?: ApiError.forbidden("missing claims: azp_name")

@JvmInline
value class Client(
    private val name: String,
) {
    fun toFagsystem(): Fagsystem =
        when (name) {
            "utsjekk", "helved-performance" -> Fagsystem.DAGPENGER
            "tiltakspenger-saksbehandling-api" -> Fagsystem.TILTAKSPENGER
            "tilleggsstonader-sak" -> Fagsystem.TILLEGGSSTØNADER
            else -> error("mangler mapping mellom app ($name) og fagsystem")
        }
}
