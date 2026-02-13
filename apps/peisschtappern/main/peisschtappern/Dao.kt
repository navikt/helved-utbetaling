package peisschtappern

import libs.jdbc.Dao
import java.sql.ResultSet
import kotlinx.coroutines.currentCoroutineContext
import libs.utils.logger
import java.sql.Types
import libs.jdbc.concurrency.connection
import libs.jdbc.map
import libs.utils.secureLog

private val daoLog = logger("dao")

enum class Table {
    avstemming,
    oppdrag,
    dryrun_aap,
    dryrun_tp,
    dryrun_ts,
    dryrun_dp,
    simuleringer,
    utbetalinger,
    saker,
    aap,
    status,
    pending_utbetalinger,
    fk,
    aapIntern,
    dpIntern,
    dp,
    tsIntern,
    tpIntern,
    ts,
    historisk,
    historiskIntern,
}

data class Daos(
    val version: String,
    val topic_name: String,
    val key: String,
    val value: String?,
    val partition: Int,
    val offset: Long,
    val timestamp_ms: Long,
    val stream_time_ms: Long,
    val system_time_ms: Long,
    val trace_id: String?,
    val commit: String? = null,
    val sakId: String? = null,
    val fagsystem: String? = null,
    val status: String? = null,
) {
    companion object: Dao<Daos> {
        override val table = "PLACEHODLER"

        override fun from(rs: ResultSet) = Daos(
            version = rs.getString("version"),
            topic_name = rs.getString("topic_name"),
            key = rs.getString("record_key"),
            value = rs.getString("record_value"),
            partition = rs.getInt("record_partition"),
            offset = rs.getLong("record_offset"),
            timestamp_ms = rs.getLong("timestamp_ms"),
            stream_time_ms = rs.getLong("stream_time_ms"),
            system_time_ms = rs.getLong("system_time_ms"),
            trace_id = rs.getString("trace_id"),
            commit = rs.getString("commit"),
            sakId = rs.getString("sak_id"),
            fagsystem = rs.getString("fagsystem"),
            status = rs.getString("status"),
        )

        suspend fun find(key: String, table: Table, limit: Int = 1000): List<Daos> {
            val sql = """
                SELECT * FROM ${table.name} 
                WHERE record_key = ? 
                ORDER BY system_time_ms DESC 
                LIMIT $limit 
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setString(1, key)
            }
        }

        suspend fun findSingle(partition: Int, offset: Long, table: Table): Daos? {
            val sql = """
                SELECT * FROM ${table.name} 
                WHERE record_partition = ? AND record_offset = ? 
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setInt(1, partition)
                stmt.setLong(2, offset)
            }.singleOrNull()
        }

        suspend fun find(
            table: Table,
            limit: Int,
            key: List<String>? = null,
            value: List<String>? = null,
            fom: Long? = null,
            tom: Long? = null,
        ): List<Daos> {
            val whereClause = if (key != null || value != null || fom != null || tom != null) {
                val keyQuery = if (key != null) " (" + key.joinToString(" OR ") { "record_key like '%$it%'" } + ") AND" else ""
                val valueQuery = if (value != null) " (" + value.joinToString(" OR ") { "record_value like '%$it%'" } + ") AND" else ""
                val fomQuery = if (fom != null) " system_time_ms > $fom AND" else ""
                val tomQuery = if (tom != null) " system_time_ms < $tom AND" else ""
                val query = "WHERE$keyQuery$valueQuery$fomQuery$tomQuery"
                query.removeSuffix(" AND").removeSuffix(" ")
            } else ""

            val sql = """
                SELECT * FROM ${table.name} 
                $whereClause
                ORDER BY system_time_ms DESC
                LIMIT $limit 
            """.trimIndent()

            return query(sql)
        }

        suspend fun findAll(
            channels: List<Channel>,
            limit: Int,
            key: List<String>? = null,
            value: List<String>? = null,
            fom: Long? = null,
            tom: Long? = null,
            traceId: String? = null,
        ): List<Daos> {
            val whereClause = if (key != null || value != null || fom != null || tom != null) {
                val keyQuery =
                    if (key != null) " (" + key.joinToString(" OR ") { "record_key like '%$it%'" } + ") AND" else ""
                val valueQuery =
                    if (value != null) " (" + value.joinToString(" OR ") { "record_value like '%$it%'" } + ") AND" else ""
                val fomQuery = if (fom != null) " system_time_ms > $fom AND" else ""
                val tomQuery = if (tom != null) " system_time_ms < $tom AND" else ""
                val traceIdQuery = if (traceId != null) " trace_id = '$traceId' AND" else ""
                val query = "WHERE$keyQuery$valueQuery$fomQuery$tomQuery$traceIdQuery"
                query.removeSuffix(" AND").removeSuffix(" ")
            } else ""

            val unionQuery = channels.joinToString(" UNION ALL ") { channel ->
                "SELECT * FROM ${channel.table.name} $whereClause"
            }

            val sql = """
                SELECT * FROM (
                    $unionQuery
                ) data
                ORDER BY system_time_ms DESC 
                LIMIT $limit 
            """.trimIndent()

            return query(sql)
        }

        suspend fun findAll(
            channels: List<Channel>,
            page: Int,
            pageSize: Int,
            key: String? = null,
            value: List<String>? = null,
            fom: Long? = null,
            tom: Long? = null,
            traceId: String? = null,
            status: List<String>? = null,
            orderBy: String? = null,
            direction: String,
        ): Page {
            val orderClause = if (orderBy != null) "ORDER BY $orderBy $direction" else ""
            val sql = """
                WITH unified AS (
                    ${channels.joinToString(" UNION ALL ") { channel -> "SELECT * FROM ${channel.table.name}" }}
                )
                SELECT *, count(*) OVER () AS total FROM unified
                WHERE record_key ILIKE COALESCE(?, record_key)
                    AND ( ?::text[] IS NULL OR EXISTS (
                        SELECT 1 FROM unnest(?::text[]) v
                        WHERE unified.record_value ILIKE '%' || v || '%'
                    ))
                    AND ( ?::text[] IS NULL OR EXISTS (
                        SELECT 1 FROM unnest(?::text[]) v
                        WHERE unified.status ILIKE v
                    ))
                    AND (? IS NULL OR system_time_ms > ?)
                    AND (? IS NULL OR system_time_ms < ?)
                    AND (? IS NULL OR trace_id = ?)
                $orderClause
                LIMIT ? OFFSET ?
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                var i = 1
                val valueArray = value?.let { currentCoroutineContext().connection.createArrayOf("text", it.toTypedArray()) }
                val statusArray = status?.let { currentCoroutineContext().connection.createArrayOf("text", it.toTypedArray()) }
                stmt.setObject(i++, key, Types.VARCHAR)
                stmt.setObject(i++, valueArray, Types.ARRAY)
                stmt.setObject(i++, valueArray, Types.ARRAY)
                stmt.setObject(i++, statusArray, Types.ARRAY)
                stmt.setObject(i++, statusArray, Types.ARRAY)
                stmt.setObject(i++, fom, Types.BIGINT)
                stmt.setObject(i++, fom, Types.BIGINT)
                stmt.setObject(i++, tom, Types.BIGINT)
                stmt.setObject(i++, tom, Types.BIGINT)
                stmt.setObject(i++, traceId, Types.VARCHAR)
                stmt.setObject(i++, traceId, Types.VARCHAR)
                stmt.setInt(i++, pageSize)
                stmt.setInt(i, (page - 1) * pageSize)
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().use { rs ->
                    var total: Int? = null

                    val rows = rs.map { r ->
                        if (total == null) total = r.getInt("total")
                        from(r)
                    }

                    Page(items = rows, total = total ?: 0)
                }
            }
        }

        suspend fun findOppdrag(sakId: String, fagsystem: String): List<Daos> {
            val sql = """
                SELECT * 
                FROM oppdrag 
                WHERE sak_id = ? AND fagsystem = ?
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setString(1, sakId)
                stmt.setString(2, fagsystem)
            }
        }

        suspend fun findStatusByKeys(keys: List<String>): List<Daos> {
            val sql = """
                SELECT *
                FROM status
                WHERE record_key IN (${keys.joinToString { "'$it'" }});
            """.trimIndent()

            return query(sql)
        }

        suspend fun findUtbetalinger(sakId: String, fagsystem: String): List<Daos> {
            val sql = """
                SELECT * 
                FROM utbetalinger 
                WHERE sak_id = ? AND fagsystem = ?
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setString(1, sakId)
                stmt.setString(2, fagsystem)
            }
        }

        suspend fun findPendingUtbetalinger(sakId: String, fagsystem: String): List<Daos> {
            val sql = """
                SELECT * 
                FROM pending_utbetalinger 
                WHERE sak_id = ? AND fagsystem = ?
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setString(1, sakId)
                stmt.setString(2, fagsystem)
            }
        }

        suspend fun findUtbetalinger(sakId: String, table: Table): List<Daos> {
            val sql = """
                SELECT *
                FROM ${table.name}
                WHERE json(record_value) ->> 'sakId' = '$sakId';
            """.trimIndent()

            return query(sql)
        }

        suspend fun findSimuleringer(sakId: String, fagsystem: String): List<Daos> {
            val sql = """
                SELECT *
                FROM simuleringer
                WHERE sak_id = ? AND fagsystem = ?
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setString(1, sakId)
                stmt.setString(2, fagsystem)
            }
        }

        suspend fun findSaker(sakId: String, fagsystem: String): List<Daos> {
            val sql = """
                SELECT *
                FROM saker
                WHERE json(record_key) ->> 'sakId' = '$sakId'
                    AND json(record_key) ->> 'fagsystem' = '$fagsystem';
            """.trimIndent()

            return query(sql)
        }
    }

    suspend fun insert(table: Table): Int {
        val sql = """
            INSERT INTO ${table.name} (
                version,
                topic_name,
                record_key,
                record_value,
                record_partition,
                record_offset,
                timestamp_ms,
                stream_time_ms,
                system_time_ms,
                trace_id,
                commit
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return update(sql) { stmt ->
            stmt.setString(1, version)
            stmt.setString(2, topic_name)
            stmt.setString(3, key)
            stmt.setString(4, value)
            stmt.setObject(5, partition)
            stmt.setObject(6, offset)
            stmt.setObject(7, timestamp_ms)
            stmt.setObject(8, stream_time_ms)
            stmt.setObject(9, system_time_ms)
            stmt.setObject(10, trace_id)
            stmt.setObject(11, commit)
        }
    }
}
