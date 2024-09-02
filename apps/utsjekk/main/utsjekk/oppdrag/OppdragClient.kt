package utsjekk.oppdrag

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import utsjekk.Config

class OppdragClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {
    suspend fun iverksettOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag) {
        val token = azure.getClientCredentialsToken(config.oppdrag.scope)

        val response = client.post("${config.oppdrag.host}/oppdrag") {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(utbetalingsoppdrag)
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
