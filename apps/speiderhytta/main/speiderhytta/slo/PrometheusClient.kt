package speiderhytta.slo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import libs.utils.appLog
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import speiderhytta.PrometheusConfig

/**
 * Minimal Prometheus HTTP API client.
 *
 * We only need `instant` queries (current value of an expression) and `range`
 * queries (for charts). Both return a `result[].value[ts, "string"]` shape.
 *
 * Unknown fields in the Prometheus response are ignored via `ignoreUnknownKeys`
 * in [libs.kotlinx.KotlinxJson].
 */
class PrometheusClient(
    private val config: PrometheusConfig,
    private val client: HttpHandler = JavaHttpClient(),
) {

    suspend fun instant(query: String): Double? {
        val url = "${config.url}/api/v1/query"
        return try {
            val response = runInterruptible {
                client(Request(Method.GET, url).query("query", query))
            }
            if (!response.status.successful) return null
            val parsed = libs.kotlinx.KotlinxJson.decodeFromString<PromResponse>(response.bodyString())
            parsed.data?.result?.firstOrNull()?.value?.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        } catch (e: CancellationException) {
            throw e
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
