@file:UseSerializers(models.kotlinx.LocalDateSerializer::class, models.kotlinx.LocalDateTimeSerializer::class)

package vedskiva

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class PeisschtappernClient(
    private val config: Config,
    private val json: Json = models.kotlinx.KotlinxJson,
    private val client: HttpClient = HttpClientFactory.new(json),
    private val azure: AzureTokenProvider = AzureTokenProvider(json, config.azure)
) {

    suspend fun oppdrag(fom: LocalDateTime, tom: LocalDateTime): List<Dao> {
        val response = client.get("${config.peisschtappern.host}/api/messages") {
            bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
            contentType(ContentType.Application.Json)
            parameter("topics", "helved.oppdrag.v1")
            parameter("pageSize", 10000)
            parameter("fom", formatter.format(fom))
            parameter("tom", formatter.format(tom))
        }

        return response.body<Page>().items
    }


}

@Serializable
data class Page(val items: List<Dao>, val total: Int)

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

private val mapper: libs.xml.XMLMapper<Oppdrag> = libs.xml.XMLMapper()

@Serializable
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
) {
    val oppdrag: Oppdrag? get() = value?.let { mapper.readValue(it) }
}
