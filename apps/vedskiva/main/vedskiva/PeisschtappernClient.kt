package vedskiva

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.LocalDate
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory

class PeisschtappernClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {
    suspend fun oppdragsData(fom: LocalDate, tom: LocalDate): List<Dao> {
        val response = client.get("${config.peisschtappern.host}/api") {
            bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
            contentType(ContentType.Application.Json)
            parameter("topics", "helved.oppdragsdata.v1")
            parameter("limit", 10000)
            parameter("fom", fom)
            parameter("tom", tom)
        }
        return response.body()
    }

}

data class Dao(
    val version: String,
    val topic_name: String,
    val key: String,
    val value: String?,
    val partition: Int,
    val offset: Long,
    val timestamp_ms: Long,
    val stream_time_ms: Long,
    val system_time_ms: Long,
)
