package smokesignal

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.appLog
import java.time.LocalDate
import java.time.LocalDateTime

interface Vedskiva {
    suspend fun next(): AvstemmingRequest
    suspend fun signal(req: AvstemmingRequest)
}

class VedskivaClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
): Vedskiva {

    override suspend fun next(): AvstemmingRequest {
        val next = client.post("${config.vedskiva.host}/api/next_range") {
            bearerAuth(azure.getClientCredentialsToken(config.vedskiva.scope).access_token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(LocalDate.now())
        }
        require(next.status == HttpStatusCode.OK)
        return next.body<AvstemmingRequest>()
    }

    override suspend fun signal(req: AvstemmingRequest) {
        val res = client.post("${config.vedskiva.host}/api/avstem") {
            bearerAuth(azure.getClientCredentialsToken(config.vedskiva.scope).access_token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(req)
        }

        if (res.status == HttpStatusCode.Conflict) {
            appLog.info("Allerede avstemt i dag")
            return 
        }
        require(res.status == HttpStatusCode.OK)
    }
}

data class AvstemmingRequest(
    val today: LocalDate,
    val fom: LocalDateTime,
    val tom: LocalDateTime,
)

