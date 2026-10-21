@file:UseSerializers(libs.kotlinx.LocalDateTimeSerializer::class, libs.kotlinx.LocalDateSerializer::class)

package branntaarn

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
import libs.utils.appLog
import org.http4k.core.*
import org.http4k.filter.ClientFilters

class PeisschtappernClient(
    config: Config,
    http: HttpHandler,
    private val json: Json = libs.kotlinx.KotlinxJson,
    azure: AzureTokenProvider = AzureTokenProvider(config.azure, http, json),
) {
    private val http = ClientFilters.SetBaseUriFrom(Uri.of(config.peisschtappern.host.toString()))
        .then(ClientFilters.BearerAuth { azure.getClientCredentialsToken(config.peisschtappern.scope).access_token })
        .then(http)

    fun manglendeKvitteringer(): List<ManglendeKvittering> {
        val response = http(Request(Method.GET, "/api/brann"))
        if (!response.status.successful) {
            appLog.warn("klarte ikke hente branner fra peisschtappern: ${response.status}")
            return emptyList()
        }
        return json.decodeFromString(response.bodyString())
    }

    fun pendingMismatches(): List<PendingMismatch> {
        val response = http(
            Request(Method.GET, "/api/brann/pending-mismatch")
                .query("since", since(Duration.ofHours(1)))
        )
        if (!response.status.successful) {
            appLog.warn("klarte ikke hente pending mismatches fra peisschtappern: ${response.status}")
            return emptyList()
        }
        return json.decodeFromString(response.bodyString())
    }

    fun dobbeltutbetalinger(): List<Dobbeltutbetaling> {
        val response = http(
            Request(Method.GET, "/api/brann/dobbeltutbetalinger")
                .query("since", since(Duration.ofHours(24)))
        )
        if (!response.status.successful) {
            appLog.warn("klarte ikke hente dobbeltutbetalinger fra peisschtappern: ${response.status}")
            return emptyList()
        }
        return json.decodeFromString(response.bodyString())
    }
    fun slukk(brann: Brann) {
        val request = when (brann) {
            is ManglendeKvittering -> Request(Method.DELETE, "/api/brann/${brann.key}")
            is Dobbeltutbetaling -> Request(Method.POST, "/api/brann/dobbeltutbetalinger/slukk")
                .query("behandlingId", brann.behandlingId)
                .query("klassekode", brann.klassekode)
                .query("fom", brann.fom.toString())
                .query("tom", brann.tom.toString())
            is PendingMismatch -> return
        }
        val response = http(request)
        if (!response.status.successful) {
            appLog.warn("klarte ikke slukke brann i peisschtappern: ${request.method} ${request.uri.path} ga ${response.status}")
        }
    }

    private fun since(duration: Duration): String = (Instant.now() - duration).toEpochMilli().toString()
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
