package peisschtappern

import kotlinx.serialization.Serializable
import libs.kotlinx.KotlinxJson
import models.Utbetaling

object PendingMismatchService {
    suspend fun detectMismatches(fom: Long, tom: Long): List<PendingMismatch> {
        val entries = Daos.messages(listOf(Channel.Utbetalinger), fom, tom)
            .mapNotNull { dao -> decode<Utbetaling>(dao.value)?.let { dao to it } }
        val uids = entries.map { it.second.uid.toString() }.distinct()
        val pendingByUid = if (uids.isEmpty()) emptyMap() else
            Daos.findPendingByUids(uids, tom)
                .mapNotNull { dao -> decode<Utbetaling>(dao.value)?.let { dao to it } }
                .groupBy { it.second.uid }

        return entries.mapNotNull { (dao, utbetaling) ->
            val latestPrecedingPending = pendingByUid[utbetaling.uid]
                ?.lastOrNull { it.first.system_time_ms < dao.system_time_ms }

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
