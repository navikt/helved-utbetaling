package peisschtappern

import kotlinx.serialization.Serializable
import libs.kotlinx.KotlinxJson
import models.Utbetaling

object PendingMismatchService {
    suspend fun detectMismatches(fom: Long, tom: Long): List<PendingMismatch> {
        val mismatches = mutableListOf<PendingMismatch>()
        Daos.forEachUtbetalingMedForrigePending(fom, tom) { row ->
            val utbetaling = decode<Utbetaling>(row.utbetaling)
            val pending = decode<Utbetaling>(row.pending)
            if (utbetaling != null && pending != null &&
                (utbetaling.perioder != pending.perioder || utbetaling.lastPeriodeId != pending.lastPeriodeId)
            ) {
                mismatches += PendingMismatch(utbetaling.uid.toString(), row.sakId, row.fagsystem)
            }
        }
        return mismatches
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

data class UtbetalingMedPending(
    val sakId: String?,
    val fagsystem: String?,
    val utbetaling: String?,
    val pending: String?,
)
