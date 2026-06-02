package simulering

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import libs.utils.appLog
import libs.utils.secureLog
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.http4k.client.JavaHttpClient
import org.http4k.filter.ServerFilters
import org.http4k.routing.routes
import org.http4k.server.ServerConfig
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import org.http4k.core.*
import org.http4k.filter.MicrometerMetrics
import models.ApiError
import java.time.Duration

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val config = Config()
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val app = simulering(config, prometheus)
    app.asServer(SunHttpLoom(8080, ServerConfig.StopMode.Graceful(Duration.ofSeconds(50)))).start().block()
}

fun simulering(config: Config, prometheus: PrometheusMeterRegistry): HttpHandler {
    val http = JavaHttpClient()
    val azure = AzureTokenProvider(config.azure, http)
    val proxyAuth: () -> String = { "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}" }
    val sts = StsClient(config.simulering.sts, http, proxyAuth = proxyAuth)
    val soap = SoapClient(config.simulering, sts, http, proxyAuth = proxyAuth)
    val service = SimuleringService(soap, sts)

    return errorFilter
        .then(ServerFilters.MicrometerMetrics.RequestTimer(prometheus))
        .then(ServerFilters.MicrometerMetrics.RequestCounter(prometheus))
        .then(routes(
            actuatorRoutes(prometheus),
            simuleringRoutes(service),
        ))
}

fun simulering(config: Config): HttpHandler {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    return simulering(config, prometheus)
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
            val dto = ApiErrorDto(e.statusCode, e.msg, e.doc, e.system?.name)
            Response(Status(e.statusCode, ""))
                .header("Content-Type", "application/json")
                .body(errorJson.encodeToString(dto))
        } catch (e: Throwable) {
            val msg = "Uhåndtert feil - Helved har fått beskjed."
            appLog.error(msg, e)
            Response(Status.INTERNAL_SERVER_ERROR).body(msg)
        }
    }
}
