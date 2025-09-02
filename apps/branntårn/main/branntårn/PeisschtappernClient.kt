package branntårn

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import io.ktor.http.contentType
import java.time.LocalDateTime
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory

class PeisschtappernClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {

    fun branner(): List<Brann> {
        return runBlocking {
            val response = client.get("${config.peisschtappern.host}/api/brann") {
                bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                contentType(ContentType.Application.Json)
            }
            response.body()
        }
    }

    fun slukk(brann: Brann) {
        runBlocking {
            client.delete("${config.peisschtappern.host}/api/brann/${brann.key}") {
                bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
            }
        }
    }

}

data class Brann(
    val key: String,
    val timeout: LocalDateTime,
    val sakId: String,
    val fagsystem: String,
)
