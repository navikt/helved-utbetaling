package statistikkern

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.StandardTableDefinition
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.TableInfo
import java.time.Instant
import java.util.concurrent.TimeUnit
import libs.utils.appLog
import libs.utils.env
import models.StatusReply
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class BigQueryService(
    val projectId: String = env("GCP_TEAM_PROJECT_ID"),
    val datasetName: String = "helved_stats",
    val bigQuery: BigQuery = BigQueryOptions.getDefaultInstance().service,
) {
    val perioderTableId = getOrCreateTable(
        name = "utbetalinger",
        schema = Schema.of(
            Field.of("key", StandardSQLTypeName.STRING),
            Field.of("fagsystem", StandardSQLTypeName.STRING),
            Field.of("stonad", StandardSQLTypeName.STRING),
            Field.of("belop", StandardSQLTypeName.NUMERIC),
            Field.of("sendt", StandardSQLTypeName.TIMESTAMP)
        )
    )

    fun upsertUtbetaling(utbetaling: Utbetaling, systemTimeMs: Long?) {
        if (utbetaling.dryrun) return

        val rows = utbetaling.perioder.map { periode ->
            val rowId = "${utbetaling.originalKey}-${periode.fom}-${periode.tom}"
            InsertAllRequest.RowToInsert.of(
                rowId,
                mapOf(
                    "key"              to utbetaling.originalKey,
                    "fagsystem"        to utbetaling.fagsystem.toName(),
                    "stonad"           to utbetaling.stønad.name,
                    "belop"            to periode.beløp.toLong(),
                    "sendt"            to systemTimeMs?.let { Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(it)).toString() }
                ).filterValues { it != null }
            )
        }

        val request = InsertAllRequest.newBuilder(perioderTableId)
            .apply { rows.forEach { addRow(it) } }
            .build()

        val response = bigQuery.insertAll(request)
        if (response.hasErrors()) {
            appLog.error("BQ insert feilet for key=${utbetaling.originalKey}: ${response.insertErrors}")
        }
    }

    val statusTableId = getOrCreateTable(
        name = "status",
        schema = Schema.of(
            Field.newBuilder("key", StandardSQLTypeName.STRING).setMode(Field.Mode.REQUIRED).build(),
            Field.of("status", StandardSQLTypeName.STRING),
            Field.of("sendt", StandardSQLTypeName.TIMESTAMP),
            Field.of("fagsystem", StandardSQLTypeName.STRING),
        )
    )

    fun upsertStatus(key: String, status: StatusReply, systemTimeMs: Long?, fagsystem: String?) {
        if (status.simulering) return
        if (status.status.name !in setOf("FEILET", "OK")) return

        insert(statusTableId, key, mapOf(
            "key"       to key,
            "status"    to status.status.name,
            "sendt"     to systemTimeMs?.let { Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(it)).toString() },
            "fagsystem" to fagsystem
            ))
    }

    val oppdragTableId = getOrCreateTable(
        name = "oppdrag",
        schema = Schema.of(
            Field.of("behandling", StandardSQLTypeName.STRING),
            Field.of("sak", StandardSQLTypeName.STRING),
            Field.of("sendt", StandardSQLTypeName.TIMESTAMP),
            Field.of("kvittert", StandardSQLTypeName.STRING),
            Field.of("fagområde", StandardSQLTypeName.STRING),
        )
    )

    fun upsertOppdrag(oppdrag: Oppdrag, systemTimeMs: Long?) {
        val oppdrag110 = oppdrag.oppdrag110 ?: return
        val henvisning = oppdrag110.oppdragsLinje150s.firstOrNull()?.henvisning
        val sakId = oppdrag110.fagsystemId
        val tidspktMelding = oppdrag110.avstemming115?.tidspktMelding
        val fagsystem = oppdrag110.kodeFagomraade

        val key = "$sakId-$henvisning"
        insert(oppdragTableId, key, mapOf(
            "behandling"     to henvisning,
            "sak"            to sakId,
            "sendt"          to systemTimeMs?.let { Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(it)).toString() },
            "kvittert"       to tidspktMelding,
            "fagområde"      to fagsystem,
        ))
    }

    private fun getOrCreateTable(name: String, schema: Schema): TableId {
        val tableId = TableId.of(projectId, datasetName, name)
        val existingTable = bigQuery.getTable(tableId)

        if (existingTable == null) {
            appLog.info("Oppretter BQ-tabell: $name")
            val tableInfo = TableInfo.newBuilder(tableId, StandardTableDefinition.of(schema)).build()
            return bigQuery.create(tableInfo).tableId
        }

        val existingFields = existingTable.getDefinition<StandardTableDefinition>().schema?.fields?.map { it.name }?.toSet() ?: emptySet()
        val newFields = schema.fields.filter { it.name !in existingFields }

        if (newFields.isNotEmpty()) {
            appLog.info("Oppdaterer BQ-tabell $name med nye felter: ${newFields.map { it.name }}")
            val updatedSchema = Schema.of(existingTable.getDefinition<StandardTableDefinition>().schema!!.fields + newFields)
            val updatedTable = existingTable.toBuilder().setDefinition(StandardTableDefinition.of(updatedSchema)).build()
            updatedTable.update()
        }

        return tableId
    }

    private fun insert(tableId: TableId, uid: String, row: Map<String, Any?>) {
        val request = InsertAllRequest.newBuilder(tableId)
            .addRow(uid, row)
            .build()

        val response = bigQuery.insertAll(request)
        if (response.hasErrors()) {
            appLog.error("BQ insert feilet for uid=$uid tabell=${tableId.table}: ${response.insertErrors}")
        }
    }
}
