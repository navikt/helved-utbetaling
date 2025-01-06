package utsjekk.clients

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import utsjekk.Config
import utsjekk.appLog
import utsjekk.utbetaling.*

interface Oppdrag {
    // v1
    suspend fun avstem(grensesnittavstemming: GrensesnittavstemmingRequest)
    suspend fun iverksettOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag)
    suspend fun hentStatus(oppdragIdDto: OppdragIdDto): OppdragStatusDto

    // v2
    suspend fun utbetal(utbetalingsoppdrag: UtbetalingsoppdragDto)
    suspend fun utbetalStatus(uid: UtbetalingId): OppdragStatusDto
}

class OppdragClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) : Oppdrag {

    override suspend fun avstem(grensesnittavstemming: GrensesnittavstemmingRequest) {
        val token = azure.getClientCredentialsToken(config.oppdrag.scope)

        val response = client.post("${config.oppdrag.host}/grensesnittavstemming") {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(grensesnittavstemming)
        }

        require(response.status == HttpStatusCode.Created) {
            "(${response.status}) Feil ved grensesnittavstemming mot utsjekk-oppdrag: ${response.bodyAsText()}"
        }
    }

    override suspend fun iverksettOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag) {
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

        when (response.status.value) {
            201 -> {}
            409 -> appLog.info("Oppdrag er allerede sendt for saksnr ${utbetalingsoppdrag.saksnummer}")
            else -> throw HttpError(body, response.status)
        }
    }

    override suspend fun hentStatus(oppdragIdDto: OppdragIdDto): OppdragStatusDto {
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

    override suspend fun utbetal(utbetalingsoppdrag: UtbetalingsoppdragDto) {
        val token = azure.getClientCredentialsToken(config.oppdrag.scope)
        val uid =utbetalingsoppdrag.uid.id 
        val response = client.post("${config.oppdrag.host}/utbetalingsoppdrag/$uid") {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(utbetalingsoppdrag)
        }

        val body = runCatching {
            response.bodyAsText()
        }.getOrElse {
            "Unknown ${response.status} error"
        }

        when (response.status.value) {
            in 200..299 -> {}
            409 -> appLog.info("Oppdrag er allerede sendt for saksnr ${utbetalingsoppdrag.saksnummer}")
            else -> throw HttpError(body, response.status)
        }

    }

    override suspend fun utbetalStatus(uid: UtbetalingId): OppdragStatusDto {
        val token = azure.getClientCredentialsToken(config.oppdrag.scope)
        val response = client.get("${config.oppdrag.host}/utbetalingsoppdrag/${uid.id}/status") {
            bearerAuth(token.access_token)
            accept(ContentType.Application.Json)
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body<OppdragStatusDto>()
            HttpStatusCode.NotFound -> throw HttpError("Fant ikke status for utbetaling $uid", response.status)
            else -> throw HttpError("Feil fra utsjekk-oppdrag: ${response.bodyAsText()}", response.status)
        }
    }
}

class HttpError(override val message: String, val code: HttpStatusCode) : RuntimeException("$code: $message")
