package utsjekk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.kafka.vanilla.Kafka
import libs.kafka.vanilla.KafkaConfig
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
import utsjekk.utbetaling.UtbetalingStatusTaskStrategy
import utsjekk.utbetaling.UtbetalingTaskStrategy
import utsjekk.utbetaling.simulering.SimuleringService
import utsjekk.utbetaling.utbetalingRoute
import java.io.File

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080) {
        val config = Config()
        val metrics = telemetry()
        database(config.jdbc)
        val statusProducer = kafka(config.kafka)
        val unleash = featureToggles(config.unleash)
        val iverksettinger = iverksetting(unleash, statusProducer)
        scheduler(config, iverksettinger, metrics)
        routes(config, iverksettinger, metrics)
    }.start(wait = true)
}

fun Application.telemetry(metrics: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)): PrometheusMeterRegistry {
    install(MicrometerMetrics) {
        registry = metrics
        meterBinders += LogbackMetrics()
    }
    appLog.info("setup telemetry")
    return metrics
}

fun Application.database(config: JdbcConfig) {
    Jdbc.initialize(config)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(File("migrations")).migrate()
        }
    }
    appLog.info("setup database")
}

fun Application.kafka(
    config: KafkaConfig,
    statusProducer: Kafka<StatusEndretMelding> = StatusKafkaProducer(config),
): Kafka<StatusEndretMelding> {
    monitor.subscribe(ApplicationStopping) {
        statusProducer.close()
    }
    appLog.info("setup kafka")
    return statusProducer
}

fun Application.featureToggles(
    config: UnleashConfig,
    featureToggles: FeatureToggles = UnleashFeatureToggles(config),
): FeatureToggles {
    appLog.info("setup featureToggles")
    return featureToggles
}

fun Application.iverksetting(
    featureToggles: FeatureToggles,
    statusProducer: Kafka<StatusEndretMelding>,
): Iverksettinger {
    appLog.info("setup iverksettinger")
    return Iverksettinger(featureToggles, statusProducer)
}

fun Application.scheduler(
    config: Config,
    iverksettinger: Iverksettinger,
    metrics: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
) {
    val oppdrag = OppdragClient(config)
    val scheduler =
        TaskScheduler(
            listOf(
                IverksettingTaskStrategy(oppdrag, iverksettinger),
                StatusTaskStrategy(oppdrag, iverksettinger),
                AvstemmingTaskStrategy(oppdrag).apply {
                    runBlocking {
                        withContext(Jdbc.context) {
                            initiserAvstemmingForNyeFagsystemer()
                        }
                    }
                },
                UtbetalingTaskStrategy(oppdrag),
                UtbetalingStatusTaskStrategy(oppdrag),
            ),
            LeaderElector(config),
            metrics,
        )

    monitor.subscribe(ApplicationStopping) {
        scheduler.close()
    }
    appLog.info("setup scheduler")
}

fun Application.routes(
    config: Config,
    iverksettinger: Iverksettinger,
    metrics: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
) {
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
            appLog.info(
                "${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms",
            )
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
                        doc = "https://navikt.github.io/utsjekk-docs/",
                    )
                    call.respond(HttpStatusCode.BadRequest, res)
                }

                else -> {
                    secureLog.error("Intern feil", cause)
                    val res = ApiError.Response(
                        msg = "Intern feil, årsaken logges av sikkerhetsmessig grunn i secureLog.", 
                        field = null,
                        doc = "https://navikt.github.io/utsjekk-docs/"
                    ) 
                    call.respond(HttpStatusCode.InternalServerError, res)
                }
            }
        }
    }

    val simulering = SimuleringClient(config)
    val simuleringService = SimuleringService(simulering)
    val simuleringValidator = SimuleringValidator(iverksettinger)

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksetting(iverksettinger)
            simulering(simuleringValidator, simulering)
            utbetalingRoute(simuleringService)
            tasks()
        }

        probes(metrics)
    }

    appLog.info("setup routes")
}

fun ApplicationCall.navident(): String =
    principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
        ?: forbidden("missing JWT claim", "NAVident", "https://navikt.github.io/utsjekk-docs/kom_i_gang")

fun ApplicationCall.client(): Client =
    principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        ?.split(":")
        ?.last()
        ?.let(::Client)
        ?: forbidden("missing JWT claim", "azp_name", "https://navikt.github.io/utsjekk-docs/kom_i_gang")

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
                    doc = "https://navikt.github.io/utsjekk-docs/kom_i_gang",
                )
        }
}
