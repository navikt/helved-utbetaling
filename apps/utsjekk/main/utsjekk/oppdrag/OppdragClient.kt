package utsjekk.oppdrag

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
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

    suspend fun hentStatus(oppdragIdDto: OppdragIdDto): OppdragStatusDto {
        val token = azure.getClientCredentialsToken(config.oppdrag.scope)

        val response = client.post("${config.oppdrag.host}/status") {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(oppdragIdDto)
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body<OppdragStatusDto>()
            HttpStatusCode.NotFound -> throw HttpError("Fant ikke status for oppdrag $oppdragIdDto", response.status)
            else -> throw HttpError("Feil fra utsjekk-oppdrag: ${response.bodyAsText()}", response.status)
        }
    }
}

class HttpError(override val message: String, val code: HttpStatusCode) : RuntimeException(message)
