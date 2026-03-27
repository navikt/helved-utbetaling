package peisschtappern

import io.ktor.http.Parameters
import models.Fagsystem
import java.time.Instant
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.text.slice
import kotlin.text.startsWith

fun Parameters.strings(key: String) =
    this[key]?.split(",")

fun Parameters.milliseconds(key: String) =
    this[key]
        ?.runCatching { Instant.parse(this).toEpochMilli() }
        ?.getOrNull()

fun Parameters.fagsystem() =
    this["fagsystem"]
        ?.split(",")
        ?.flatMap { fagsystem ->
            listOf(
                listOf("TILTPENG", "TILTAKSPENGER"),
                listOf("TILLSTPB", "TILLSTLM", "TILLSTDR", "TILLSTBO", "TILLST", "TILLEGGSSTØNADER"),
                listOf("DP", "DAGPENGER"),
                listOf("HELSREF", "HISTORISK"),
                listOf("AAP")
            ).find {
                it.contains(fagsystem)
            } ?: emptyList()
        }

fun Parameters.orderBy() =
    when (this["orderBy"]) {
        "offset" -> "record_offset"
        "timestamp" -> "system_time_ms"
        null -> null
        else -> error("Invalid orderBy parameter: ${this["orderBy"]}")
    }

fun Parameters.direction() =
    when (this["direction"]) {
        "ASC" -> "ASC"
        null, "DESC" -> "DESC"
        else -> error("Invalid direction parameter: ${this["direction"]}")
    }

fun Parameters.include() =
    this
        .strings("value")
        ?.filter { !it.startsWith("!") && !it.startsWith("not:") }
        ?.takeIf { it.isNotEmpty() }

fun Parameters.exclude() =
    this
        .strings("value")
        ?.filter { it.startsWith("!") || it.startsWith("not:") }
        ?.map {
            if (it.startsWith("!")) {
                it.slice(IntRange(1, it.length - 1))
            } else if (it.startsWith("not:")) {
                it.slice(IntRange(4, it.length - 1))
            } else {
                it
            }
        }
        ?.takeIf { it.isNotEmpty() }

fun Fagsystem.tables(): Triple<Table?, Table, Table?> =
    when (this) {
        Fagsystem.AAP -> Triple(Table.aap, Table.aapIntern, Table.dryrun_aap)
        Fagsystem.DAGPENGER -> Triple(Table.dp, Table.dpIntern, Table.dryrun_dp)
        Fagsystem.TILTAKSPENGER -> Triple(null, Table.tpIntern, Table.dryrun_tp)
        Fagsystem.TILLSTPB,
        Fagsystem.TILLSTLM,
        Fagsystem.TILLSTBO,
        Fagsystem.TILLSTDR,
        Fagsystem.TILLSTRS,
        Fagsystem.TILLSTRO,
        Fagsystem.TILLSTRA,
        Fagsystem.TILLSTFL,
        Fagsystem.TILLEGGSSTØNADER -> Triple(Table.ts, Table.tsIntern, Table.dryrun_ts)
        Fagsystem.HISTORISK -> Triple(Table.historisk, Table.historiskIntern, null)
    }