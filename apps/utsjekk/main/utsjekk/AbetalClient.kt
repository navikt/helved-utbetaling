package utsjekk

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import utsjekk.utbetaling.Utbetaling
import utsjekk.utbetaling.UtbetalingId


class AbetalClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {

    @Deprecated("ikke gjør kall mot state stores")
    suspend fun utbetaling(uid: UtbetalingId): Utbetaling {
        val response = client.get("${config.abetal.host}/api/utbetalinger${uid.id}") {
            bearerAuth(azure.getClientCredentialsToken(config.abetal.scope).access_token)
            contentType(ContentType.Application.Json)
        }
        return response.body()
    }

    @Deprecated("ikke gjør kall mot state stores")
    suspend fun exists(uid: UtbetalingId): Boolean {
        val response = client.get("${config.abetal.host}/api/utbetalinger/${uid.id}") {
            bearerAuth(azure.getClientCredentialsToken(config.abetal.scope).access_token)
            contentType(ContentType.Application.Json)
            expectSuccess = false
        }
        return response.status == HttpStatusCode.OK
    }
}
