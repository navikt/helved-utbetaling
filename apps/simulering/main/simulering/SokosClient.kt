package simulering

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request

class SokosClient(
    private val config: SokosOppdragConfig,
    private val http: HttpHandler,
    private val authorization: () -> String,
    private val json: Json = Json(libs.kotlinx.KotlinxJson) { explicitNulls = false }
) {
    fun simulerDto(request: SimuleringRequest, comparison: ShadowComparison): ResponsSimulering {
        val body = json.encodeToString(request)
        comparison.log("rest_request") { put("restRequest", json.parseToJsonElement(body)) }
        val url = config.host.toString().trimEnd('/') + "/api/osSimulerBeregningApi/simulerBeregning"
        val response = http(Request(Method.POST, url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", authorization())
            .body(body))
        val responseBody = response.bodyString()
        comparison.log("rest_response") {
            put("httpStatus", response.status.code)
            put("restResponse", responseBody)
        }
        check(response.status.successful) { "Sokos shadow returned HTTP ${response.status.code}" }
        return json.decodeFromString<SimuleringResponse>(responseBody).operation.output.responsSimulering
    }
}

