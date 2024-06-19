package utsjekk.oppdrag

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import utsjekk.Config
import utsjekk.OppdragConfig

class OppdragClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {
    suspend fun sendOppdrag(oppdrag: String) {
        val token = azure.getClientCredentialsToken(config.oppdrag.scope)

        val response = client.post("${config.oppdrag.host}/oppdrag") {
            bearerAuth(token.access_token)
            setBody(oppdrag)
        }

        val body = runCatching {
            response.bodyAsText()
        }.getOrElse {
            "Unknown ${response.status} error"
        }

        when {
            response.status.isSuccess() -> {}
            else -> throw HttpError(body, response.status)
        }
    }
}

class HttpError(override val message: String, val code: HttpStatusCode) : RuntimeException(message)
