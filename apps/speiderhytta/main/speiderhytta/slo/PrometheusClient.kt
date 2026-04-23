package speiderhytta.slo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import libs.http.HttpClientFactory
import libs.utils.appLog
import speiderhytta.PrometheusConfig

/**
 * Minimal Prometheus HTTP API client.
 *
 * We only need `instant` queries (current value of an expression) and `range`
 * queries (for charts). Both return a `result[].value[ts, "string"]` shape.
 *
 * Unknown fields in the Prometheus response are ignored — the default
 * [HttpClientFactory] mapper already disables FAIL_ON_UNKNOWN_PROPERTIES.
 */
class PrometheusClient(
    private val config: PrometheusConfig,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.INFO),
) {

    suspend fun instant(query: String): Double? {
        val url = "${config.url}/api/v1/query"
        return try {
            val response: PromResponse = client.get(url) { parameter("query", query) }.body()
            response.data?.result?.firstOrNull()?.value?.getOrNull(1)?.toString()?.toDoubleOrNull()
        } catch (t: Throwable) {
            appLog.warn("prometheus instant query failed: {}", query, t)
            null
        }
    }

    private data class PromResponse(val status: String, val data: PromData?)

    private data class PromData(val resultType: String?, val result: List<PromResult> = emptyList())

    private data class PromResult(val metric: Map<String, String> = emptyMap(), val value: List<Any>? = null)
}
