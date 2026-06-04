package peisschtappern

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

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

private fun parsePeriode(json: JsonNode): Periode? {
    val fom = json.textOrNull("fom") ?: return null
    val tom = json.textOrNull("tom") ?: return null
    val beløp = json.longOrNull("beløp") ?: return null
    return Periode(fom, tom, beløp)
}

private fun parseEntry(dao: Daos): UtbetalingEntry? {
    val value = dao.value?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val json = objectMapper.readTree(value)
        val uid = json.textOrNull("uid") ?: return null
        val perioder = json.get("perioder")
            ?.mapNotNull(::parsePeriode)
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

private val objectMapper = ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private fun JsonNode.textOrNull(field: String): String? =
    get(field)?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotEmpty() }

private fun JsonNode.longOrNull(field: String): Long? =
    get(field)?.takeIf { !it.isNull }?.asLong()

internal fun parseUid(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        objectMapper.readTree(value).textOrNull("uid")
    } catch (_: Exception) {
        null
    }
}
