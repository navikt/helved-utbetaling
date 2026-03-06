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
import java.time.format.DateTimeFormatter
import libs.utils.appLog
import libs.utils.env
import models.StatusReply
import models.Utbetaling

class BigQueryService(
    val projectId: String = env("GCP_TEAM_PROJECT_ID"),
    val datasetName: String = "helved_utbetaling",
    val bigQuery: BigQuery = BigQueryOptions.getDefaultInstance().service,
) {
    val perioderTableId = getOrCreateTable(
        name = "utbetalinger",
        schema = Schema.of(
            Field.of("key", StandardSQLTypeName.STRING),
            Field.of("fagsystem", StandardSQLTypeName.STRING),
            Field.of("stonad", StandardSQLTypeName.STRING),
            Field.of("belop", StandardSQLTypeName.NUMERIC),
            Field.of("fom", StandardSQLTypeName.DATE),
            Field.of("tom", StandardSQLTypeName.DATE),
            Field.of("vedtakstidspunkt", StandardSQLTypeName.DATETIME),
            Field.of("processed_at", StandardSQLTypeName.TIMESTAMP), // TODO: Blir det riktig med record.timestamp her?
            Field.of("inserted_at", StandardSQLTypeName.TIMESTAMP),
        )
    )

    fun upsertUtbetaling(utbetaling: Utbetaling, timestampMs: Long?) {
        if (utbetaling.dryrun) return

        val processedAt = timestampMs?.let { Instant.ofEpochMilli(it) }.toString()

        val rows = utbetaling.perioder.map { periode ->
            InsertAllRequest.RowToInsert.of(
                mapOf(
                    "key"              to utbetaling.originalKey,
                    "fagsystem"        to utbetaling.fagsystem.toName(),
                    "stonad"           to utbetaling.stønad.name,
                    "belop"            to periode.beløp.toLong(),
                    "fom"              to periode.fom.toString(),
                    "tom"              to periode.tom.toString(),
                    "vedtakstidspunkt" to utbetaling.vedtakstidspunkt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                    "processed_at"     to processedAt,
                    "inserted_at"      to Instant.now().toString(),
                )
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
        )
    )

    fun upsertStatus(key: String, status: StatusReply) {
        insert(statusTableId, key, mapOf(
            "key"    to key,
            "status" to status.status.name,
        ))
    }

    private fun getOrCreateTable(name: String, schema: Schema): TableId {
        val tableId = TableId.of(projectId, datasetName, name)
        return bigQuery.getTable(tableId)?.tableId ?: run {
            appLog.info("Oppretter BQ-tabell: $name")
            val tableInfo = TableInfo.newBuilder(tableId, StandardTableDefinition.of(schema)).build()
            bigQuery.create(tableInfo).tableId
        }
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
