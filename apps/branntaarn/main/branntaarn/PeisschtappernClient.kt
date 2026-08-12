@file:UseSerializers(libs.kotlinx.LocalDateTimeSerializer::class, libs.kotlinx.LocalDateSerializer::class)

package branntaarn

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.appLog

class PeisschtappernClient(
    private val config: Config,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val client: HttpClient = HttpClientFactory.new(json, LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(json, config.azure)
) {

    fun manglendeKvitteringer(): List<ManglendeKvittering> {
        return try {
            runBlocking {
                val response = client.get("${config.peisschtappern.host}/api/brann") {
                    bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                }
                response.body()
            }
        } catch (e: ConnectTimeoutException) {
            appLog.warn("klarte ikke hente branner fra peisschtappern", e)
            emptyList()
        }
    }

    fun pendingMismatches(): List<PendingMismatch> {
        return try {
            runBlocking {
                val since = (Instant.now() - Duration.ofHours(1)).toEpochMilli()
                val response = client.get("${config.peisschtappern.host}/api/brann/pending-mismatch") {
                    bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                    parameter("since", since)
                }
                response.body()
            }
        } catch (e: ConnectTimeoutException) {
            appLog.warn("klarte ikke hente pending mismatches fra peisschtappern", e)
            emptyList()
        }
    }

    fun dobbeltutbetalinger(): List<Dobbeltutbetaling> {
        return try {
            runBlocking {
                val since = (Instant.now() - Duration.ofHours(24)).toEpochMilli()
                val response = client.get("${config.peisschtappern.host}/api/brann/dobbeltutbetalinger") {
                    bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                    parameter("since", since)
                }
                response.body()
            }
        } catch (e: ConnectTimeoutException) {
            appLog.warn("klarte ikke hente dobbeltutbetalinger fra peisschtappern", e)
            emptyList()
        }
    }

    fun slukk(brann: Brann) {
        try {
            runBlocking {
                when (brann) {
                    is ManglendeKvittering -> client.delete("${config.peisschtappern.host}/api/brann/${brann.key}") {
                        bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                    }
                    is Dobbeltutbetaling -> client.post("${config.peisschtappern.host}/api/brann/dobbeltutbetalinger") {
                        bearerAuth(azure.getClientCredentialsToken(config.peisschtappern.scope).access_token)
                        parameter("behandlingId", brann.behandlingId)
                        parameter("klassekode", brann.klassekode)
                        parameter("fom", brann.fom)
                        parameter("tom", brann.tom)
                    }
                    is PendingMismatch -> Unit

                }
            }
        } catch (e: ConnectTimeoutException) {
            appLog.warn("klarte ikke slukke brann fra peisschtappern", e)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("@type")
sealed interface Brann {
    val sakId: String?
    val fagsystem: String?
}

@Serializable
@SerialName("manglende_kvittering")
data class ManglendeKvittering(
    val key: String,
    val timeout: LocalDateTime,
    override val sakId: String,
    override val fagsystem: String,
) : Brann

@Serializable
data class PendingMismatch(
    val uid: String,
    override val sakId: String?,
    override val fagsystem: String?,
) : Brann

@Serializable
@SerialName("dobbelt_utbetaling")
data class Dobbeltutbetaling(
    val behandlingId: String,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val kilder: Map<String, Kilde>,
    override val sakId: String? = null,
    override val fagsystem: String? = null,
) : Brann {
    val antallKilder: Int get() = kilder.size

    @Serializable
    data class Kilde(
        val key: String,
        val partition: Int,
        val offset: Long,
        val timestampMs: Long,
    )
}
