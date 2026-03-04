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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import libs.utils.appLog
import libs.utils.env
import models.StatusReply
import models.Utbetaling

class BigQueryService(
    // TODO: Stemmer disse?
    private val projectId: String = env("GOOGLE_CLOUD_PROJECT"),
    val datasetName: String = env("BIGQUERY_DATASET_HELVED_UTBETALING_DATASET_ID"),
    val bigQuery: BigQuery = BigQueryOptions.getDefaultInstance().service,
) {
    val utbetalingerTableId = getOrCreateTable(
        name = "utbetalinger",
        schema = Schema.of(
            Field.newBuilder("uid", StandardSQLTypeName.STRING).setMode(Field.Mode.REQUIRED).build(),
            Field.of("fagsystem", StandardSQLTypeName.STRING),
            Field.of("sak_id", StandardSQLTypeName.STRING),
            Field.of("total_belop", StandardSQLTypeName.NUMERIC),
            Field.of("vedtakstidspunkt", StandardSQLTypeName.TIMESTAMP),
            Field.of("fom", StandardSQLTypeName.DATE),
            Field.of("tom", StandardSQLTypeName.DATE),
        )
    )

    val statusTableId = getOrCreateTable(
        name = "status",
        schema = Schema.of(
            Field.newBuilder("uid", StandardSQLTypeName.STRING).setMode(Field.Mode.REQUIRED).build(),
            Field.of("status", StandardSQLTypeName.STRING),
        )
    )

    fun upsertUtbetaling(uid: String, utbetaling: Utbetaling) {
        if (utbetaling.dryrun) return
        insert(utbetalingerTableId, uid, mapOf(
            "uid"              to uid,
            "fagsystem"        to utbetaling.fagsystem.name,
            "sak_id"           to utbetaling.sakId.id,
            "total_belop"      to utbetaling.perioder.sumOf { it.beløp.toLong() },
            "vedtakstidspunkt" to utbetaling.vedtakstidspunkt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "fom"              to utbetaling.perioder.minOf { it.fom }.toString(),
            "tom"              to utbetaling.perioder.maxOf { it.tom }.toString(),
        ))
    }

    fun upsertStatus(uid: String, status: StatusReply) {
        insert(statusTableId, uid, mapOf(
            "uid"    to uid,
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