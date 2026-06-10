package peisschtappern

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class PendingMismatch(
    val uid: String,
    val sakId: String?,
    val fagsystem: String?,
)

private data class Periode(
    val fom: String,
    val tom: String,
    val beløp: Long,
)

private data class UtbetalingEntry(
    val dao: Daos,
    val uid: String,
    val perioder: List<Periode>,
)

private fun parsePeriode(json: JsonObject): Periode? {
    val fom = json.textOrNull("fom") ?: return null
    val tom = json.textOrNull("tom") ?: return null
    val beløp = json.longOrNull("beløp") ?: return null
    return Periode(fom, tom, beløp)
}

private fun parseEntry(dao: Daos): UtbetalingEntry? {
    val value = dao.value?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val json = Json.parseToJsonElement(value).jsonObject
        val uid = json.textOrNull("uid") ?: return null
        val perioder = (json["perioder"] as? JsonArray)
            ?.mapNotNull { parsePeriode(it.jsonObject) }
            ?.sortedWith(compareBy({ it.fom }, { it.tom }, { it.beløp }))
            ?: emptyList()
        UtbetalingEntry(dao, uid, perioder)
    } catch (_: Exception) {
        null
    }
}

fun detectMismatches(utbetalinger: List<Daos>, pendingUtbetalinger: List<Daos>): List<PendingMismatch> {
    val entries = utbetalinger.mapNotNull(::parseEntry)
    val pendingByUid = pendingUtbetalinger.mapNotNull(::parseEntry).groupBy { it.uid }

    return entries.mapNotNull { utbetaling ->
        val latestPrecedingPending = pendingByUid[utbetaling.uid]
            ?.filter { it.dao.system_time_ms < utbetaling.dao.system_time_ms }
            ?.maxByOrNull { it.dao.system_time_ms }

        latestPrecedingPending
            ?.takeIf { utbetaling.perioder != it.perioder }
            ?.let { PendingMismatch(utbetaling.uid, utbetaling.dao.sakId, utbetaling.dao.fagsystem) }
    }
}

private fun JsonObject.textOrNull(field: String): String? =
    get(field)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }

private fun JsonObject.longOrNull(field: String): Long? =
    get(field)?.jsonPrimitive?.longOrNull

internal fun parseUid(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        Json.parseToJsonElement(value).jsonObject.textOrNull("uid")
    } catch (_: Exception) {
        null
    }
}
