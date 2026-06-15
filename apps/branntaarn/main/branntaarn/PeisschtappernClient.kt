@file:UseSerializers(models.kotlinx.LocalDateTimeSerializer::class)

package branntaarn

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.appLog

class PeisschtappernClient(
    private val config: Config,
    private val json: Json = models.kotlinx.KotlinxJson,
    private val client: HttpClient = HttpClientFactory.new(json, LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(json, config.azure)
) {

    fun branner(): List<Brann> {
        return try {
            runBlocking {
                val response = client.get("${config.peisschtappern.host}/api/brann") {
                    bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                    contentType(ContentType.Application.Json)
                }
                response.body()
            }
        } catch (e: ConnectTimeoutException) {
            appLog.warn("klarte ikke hente branner fra peisschtappern", e)
            emptyList()
        }
    }

    fun pendingMismatches(): List<PendingMismatch> {
        return try {
            runBlocking {
                val since = (Instant.now() - Duration.ofHours(1)).toEpochMilli()
                val response = client.get("${config.peisschtappern.host}/api/brann/pending-mismatch") {
                    bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                    contentType(ContentType.Application.Json)
                    parameter("since", since)
                }
                response.body()
            }
        } catch (e: ConnectTimeoutException) {
            appLog.warn("klarte ikke hente pending mismatches fra peisschtappern", e)
            emptyList()
        }
    }

    fun slukk(brann: Brann) {
        try {
            runBlocking {
                client.delete("${config.peisschtappern.host}/api/brann/${brann.key}") {
                    bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                }
            }
        } catch (e: ConnectTimeoutException) {
            appLog.warn("klarte ikke slukke branner fra peisschtappern", e)
        }
    }
}

@Serializable
data class Brann(
    val key: String,
    val timeout: LocalDateTime,
    val sakId: String,
    val fagsystem: String,
)

@Serializable
data class PendingMismatch(
    val uid: String,
    val sakId: String?,
    val fagsystem: String?,
)
