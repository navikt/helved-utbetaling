@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class, libs.kotlinx.LocalDateTimeSerializer::class)

package smokesignal

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.Json
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.appLog
import libs.utils.secureLog
import java.time.LocalDate
import java.time.LocalDateTime

interface Vedskiva {
    suspend fun next(): AvstemmingRequest
    suspend fun signal(req: AvstemmingRequest)
}

class VedskivaClient(
    private val config: Config,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val client: HttpClient = HttpClientFactory.new(json, LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(json, config.azure)
): Vedskiva {

    override suspend fun next(): AvstemmingRequest {
        val next = client.post("${config.vedskiva.host}/api/next_range") {
            bearerAuth(azure.getClientCredentialsToken(config.vedskiva.scope).access_token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(DateBody(LocalDate.now()))
        }
        if (next.status != HttpStatusCode.OK) {
            appLog.error("vedskiva /api/next_range responded ${next.status}")
            secureLog.error("vedskiva /api/next_range responded ${next.status}: ${next.bodyAsText()}")
            error("vedskiva /api/next_range responded ${next.status}")
        }
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
        if (res.status != HttpStatusCode.OK) {
            appLog.error("Failed to trigger avstemming: $req => <redacted>")
            secureLog.error("Failed to trigger avstemming: $req => ${res.bodyAsText()}")
        }
    }
}

@Serializable
data class AvstemmingRequest(
    val today: LocalDate,
    val fom: LocalDateTime,
    val tom: LocalDateTime,
)

@Serializable
data class DateBody(
    val date: LocalDate,
)
