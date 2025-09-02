package branntårn

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
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

}

data class Brann(
    val start: LocalDateTime,
    val kafkaKey: String,
    val sakId: String,
    val fagsystem: String,
) 

