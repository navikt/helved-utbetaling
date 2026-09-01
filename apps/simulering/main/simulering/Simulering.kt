@file:Suppress("DEPRECATION")

package simulering

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import libs.auth.Jwt
import libs.auth.JwtVerifier
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.kafka.topology
import libs.utils.appLog
import libs.utils.secureLog
import models.ApiError
import models.Fagsystem
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.http4k.client.JavaHttpClient
import org.http4k.core.*
import org.http4k.filter.ClientFilters
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.lens.RequestContextKey
import org.http4k.routing.routes
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import simulering.v1.SimuleringServiceV1
import java.time.Duration
import kotlin.concurrent.thread

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val app = app()
    app.asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50)))).start().block()
}

fun app(
    config: Config = Config(),
    prometheus: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
    kafka: Streams = KafkaStreams(),
    http: HttpHandler = JavaHttpClient(requestModifier = { it.timeout(Duration.ofMinutes(2))}),
    azure : AzureTokenProvider = AzureTokenProvider(config.azure, http),
    proxyAuth: () -> String = { "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}" },
    sts: Sts = StsClient(config.simulering.sts, http, proxyAuth = proxyAuth),
    soap: Soap = SoapClient(config.simulering, sts, SecureLogFilter.then(http), proxyAuth = proxyAuth),
    jwtVerifier: JwtVerifier? = null,
): HttpHandler {
    val service = SimuleringService(soap, sts)
    val serviceV1 = SimuleringServiceV1(soap, sts)

    // Hvis OS timer ut/treg responstid og denne bygger seg opp, trenger vi ikke
    // fullføre alle simuleringer fordi de vil nok time ut av konsumenten sine timeouts uansett.
    // capacity = 16 sørger for at simuleringer fungerer så lenge OS er performant.
    val channel = Channel<Pair<String, SimulerBeregningRequest>>(capacity = 16)

    val backpressureChannel = Channel<Pair<String, Fagsystem>>(Channel.UNLIMITED)

    val dryrunProducers = mapOf(
        Fagsystem.AAP to kafka.createProducer(config.kafka, Topics.dryrunAap),
        Fagsystem.DAGPENGER to kafka.createProducer(config.kafka, Topics.dryrunDp),
        Fagsystem.TILLEGGSSTØNADER to kafka.createProducer(config.kafka, Topics.dryrunTs),
        Fagsystem.TILTAKSPENGER to kafka.createProducer(config.kafka, Topics.dryrunTp),
    )

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology {
            simuleringer(channel, backpressureChannel)
        }
    )

    val worker = SimuleringWorker(channel, backpressureChannel, service, dryrunProducers)
    thread(isDaemon = true, name = "simulering-workers") {
        runBlocking(Dispatchers.IO) {
            repeat(4) { launch { worker.run() } }
            launch { worker.drainBackpressure() }
        }
    }

    // Auth setup
    val contexts = RequestContexts()
    val claimsLens = RequestContextKey.required<Jwt.Claims>(contexts)
    val verifier = jwtVerifier ?: createJwtVerifier(config.azure)
    val authFilter = azureAuthFilter(verifier, claimsLens)

    // Authenticated dryrun routes
    val authenticatedRoutes = authFilter.then(dryrunRoutes(kafka, config, claimsLens))

    return ServerFilters.InitialiseRequestContext(contexts)
        .then(errorFilter)
        .then(ServerFilters.MicrometerMetrics.RequestTimer(prometheus))
        .then(ServerFilters.MicrometerMetrics.RequestCounter(prometheus))
        .then(routes(
            actuatorRoutes(prometheus),
            simuleringRoutes(serviceV1),
            authenticatedRoutes,
        ))
}

@Serializable
private data class ApiErrorDto(
    val statusCode: Int,
    val msg: String,
    val doc: String? = null,
    val system: String? = null,
)

private val errorJson = Json { encodeDefaults = true }

private val errorFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: ApiError) {
            val dto = ApiErrorDto(e.statusCode, e.msg, e.doc, e.source?.name)
            Response(Status(e.statusCode, ""))
                .header("Content-Type", "application/json")
                .body(errorJson.encodeToString(dto))
        } catch (e: Throwable) {
            val msg = "Uhåndtert feil i ${request.method} ${request.uri} - Helved har fått beskjed."
            appLog.error(msg)
            secureLog.error(msg, e)
            Response(Status.INTERNAL_SERVER_ERROR).body("Uhåndtert feil - Helved har fått beskjed.")
        }
    }
}
