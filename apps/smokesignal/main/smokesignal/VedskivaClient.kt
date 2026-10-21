@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class, libs.kotlinx.LocalDateTimeSerializer::class)

package smokesignal

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import libs.utils.Log
import models.unavailable
import org.http4k.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

interface Vedskiva {
    suspend fun next(): AvstemmingRequest
    suspend fun signal(req: AvstemmingRequest)
}

class VedskivaClient(
    private val config: Config,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val client: HttpHandler = retryingHttpClient()
): Vedskiva {
    private var token: String? = null
    private var tokenExpiresAt: Instant = Instant.MIN

    override suspend fun next(): AvstemmingRequest {
        val next = client(post("/api/next_range", json.encodeToString(DateBody(LocalDate.now()))))
        if (next.status != Status.OK) {
            Log.error("vedskiva /api/next_range responded ${next.status}: ${next.bodyString()}")
            error("vedskiva /api/next_range responded ${next.status}")
        }
        return json.decodeFromString<AvstemmingRequest>(next.bodyString())
    }

    override suspend fun signal(req: AvstemmingRequest) {
        val res = client(post("/api/avstem", json.encodeToString(req)))

        if (res.status == Status.CONFLICT) {
            Log.info("Allerede avstemt i dag")
            return 
        }
        if (res.status != Status.OK) {
            Log.error("Failed to trigger avstemming", "$req => ${res.bodyString()}")
        }
    }

    private fun post(path: String, body: String): Request =
        Request(Method.POST, "${config.vedskiva.host.toString().trimEnd('/')}$path")
            .header("Authorization", "Bearer ${getToken()}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .body(body)

    private fun getToken(): String {
        token?.takeIf { Instant.now().isBefore(tokenExpiresAt) }?.let { return it }
        val response = client(Request(Method.POST, config.azure.tokenEndpoint.toString())
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(listOf(
                "client_id" to config.azure.clientId,
                "client_secret" to config.azure.clientSecret,
                "scope" to config.vedskiva.scope,
                "grant_type" to "client_credentials"
            ).joinToString("&") { (key, value) ->
                "$key=${java.net.URLEncoder.encode(value, Charsets.UTF_8)}"
            }))
        if (!response.status.successful) {
            if (response.status.clientError && response.status.code != 429) {
                unavailable("Token provider avviste client credentials")
            }
            unavailable("Token provider er utilgjengelig")
        }
        val result = try {
            json.decodeFromString<AccessToken>(response.bodyString())
        } catch (e: Exception) {
            unavailable("Token provider er utilgjengelig")
        }
        tokenExpiresAt = Instant.now().plusSeconds(result.expires_in - 60)
        token = result.access_token
        return result.access_token
    }
}

@Serializable
private data class AccessToken(val expires_in: Long, val access_token: String)

@Serializable
data class AvstemmingRequest(
    val today: LocalDate,
    val fom: LocalDateTime,
    val tom: LocalDateTime,
)

@Serializable
data class DateBody(
    val date: LocalDate,
)
