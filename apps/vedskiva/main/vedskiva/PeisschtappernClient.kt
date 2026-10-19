@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class, libs.kotlinx.LocalDateTimeSerializer::class)

package vedskiva

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import libs.auth.AzureToken
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import libs.auth.AzureTokenProvider
import libs.auth.ProviderRejected
import libs.auth.ProviderUnavailable
import models.unavailable
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.time.Duration

class PeisschtappernClient(
    private val config: Config,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val client: HttpHandler = JavaHttpClient(requestModifier = { it.timeout(Duration.ofSeconds(60)) }),
    private val azure: AzureTokenProvider = AzureTokenProvider(json, config.azure)
) {

    suspend fun oppdrag(fom: LocalDateTime, tom: LocalDateTime): List<Dao> {
        val token = when(val tokenResponse = azure.getClientCredentialsToken(config.peisschtappern.scope)) {
            is AzureToken -> tokenResponse.access_token
            is ProviderRejected -> unavailable("Token provider avviste client credentials")
            is ProviderUnavailable -> unavailable("Token provider er utilgjengelig")
        }

        val request = Request(Method.GET, "${config.peisschtappern.host}/api/messages")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .query("topics", "helved.oppdrag.v1")
            .query("pageSize", "10000")
            .query("fom", formatter.format(fom))
            .query("tom", formatter.format(tom))

        val response = runInterruptible(Dispatchers.IO) { client(request) }

        return json.decodeFromString<Page>(response.bodyString()).items
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
