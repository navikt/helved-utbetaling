package peisschtappern

import kotlinx.serialization.Serializable
import libs.kotlinx.KotlinxJson
import models.Utbetaling
import kotlin.collections.filter

object PendingMismatchService {
    suspend fun detectMismatches(since: Long): List<PendingMismatch> {
        val daos = Daos.messages(listOf(Channel.Utbetalinger), since)
        val uids = daos.mapNotNull { decode<Uid>(it.value)?.uid }.distinct()
        val pendingByUid = if (uids.isEmpty()) emptyMap() else
            Daos.findPendingByUids(uids)
                .mapNotNull { dao -> decode<Utbetaling>(dao.value)?.let { dao to it } }
                .groupBy { it.second.uid }

        val entries = daos.mapNotNull { dao -> decode<Utbetaling>(dao.value)?.let { dao to it } }

        return entries.mapNotNull { (dao, utbetaling) ->
            val latestPrecedingPending = pendingByUid[utbetaling.uid]
                ?.filter { it.first.system_time_ms < dao.system_time_ms }
                ?.maxByOrNull { it.first.system_time_ms }

            latestPrecedingPending
                ?.takeIf { utbetaling.perioder != it.second.perioder || utbetaling.lastPeriodeId != it.second.lastPeriodeId }
                ?.let { PendingMismatch(utbetaling.uid.toString(), dao.sakId, dao.fagsystem) }
        }
    }

    private inline fun <reified T> decode(value: String?): T? {
        if (value.isNullOrBlank()) return null
        return runCatching { KotlinxJson.decodeFromString<T>(value) }.getOrNull()
    }
}

@Serializable
data class PendingMismatch(
    val uid: String,
    val sakId: String?,
    val fagsystem: String?,
)

@Serializable
private data class Uid(
    val uid: String,
)
