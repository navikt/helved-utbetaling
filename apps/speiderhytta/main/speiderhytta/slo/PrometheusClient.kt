package speiderhytta.slo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import libs.http.HttpClientFactory
import libs.utils.appLog
import speiderhytta.PrometheusConfig

/**
 * Minimal Prometheus HTTP API client.
 *
 * We only need `instant` queries (current value of an expression) and `range`
 * queries (for charts). Both return a `result[].value[ts, "string"]` shape.
 *
 * Unknown fields in the Prometheus response are ignored via `ignoreUnknownKeys`
 * in the default [HttpClientFactory] Json config.
 */
class PrometheusClient(
    private val config: PrometheusConfig,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.INFO),
) {

    suspend fun instant(query: String): Double? {
        val url = "${config.url}/api/v1/query"
        return try {
            val response: PromResponse = client.get(url) { parameter("query", query) }.body()
            response.data?.result?.firstOrNull()?.value?.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        } catch (t: Throwable) {
            appLog.warn("prometheus instant query failed: {}", query, t)
            null
        }
    }

    @Serializable
    private data class PromResponse(val status: String, val data: PromData?)

    @Serializable
    private data class PromData(val resultType: String?, val result: List<PromResult> = emptyList())

    @Serializable
    private data class PromResult(val metric: Map<String, String> = emptyMap(), val value: List<JsonElement>? = null)
}
