package peisschtappern

import libs.jdbc.concurrency.connection
import libs.jdbc.map
import libs.utils.logger
import libs.utils.secureLog
import java.sql.ResultSet
import kotlin.coroutines.coroutineContext

private val daoLog = logger("dao")

enum class Table {
    avstemming,
    oppdrag,
    dryrun_aap,
    dryrun_tp,
    dryrun_ts,
    dryrun_dp,
    kvittering,
    simuleringer,
    utbetalinger,
    saker,
    aap,
    status,
    pending_utbetalinger,
    fk,
    dpIntern,
    dp
}

data class Dao(
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
) {
    companion object {
        suspend fun find(key: String, table: Table, limit: Int = 1000): List<Dao> {
            val sql = """
                SELECT * FROM ${table.name} 
                WHERE record_key = ? 
                ORDER BY timestamp_ms DESC 
                LIMIT $limit 
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        suspend fun find(
            table: Table,
            limit: Int,
            key: List<String>? = null,
            value: List<String>? = null,
            fom: Long? = null,
            tom: Long? = null,
        ): List<Dao> {
            val whereClause = if (key != null || value != null || fom != null || tom != null) {
                val keyQuery = if (key != null) " (" + key.joinToString(" OR ") { "record_key like '%$it%'" } + ") AND" else ""
                val valueQuery = if (value != null) " (" + value.joinToString(" OR ") { "record_value like '%$it%'" } + ") AND" else ""
                val fomQuery = if (fom != null) " timestamp_ms > $fom AND" else ""
                val tomQuery = if (tom != null) " timestamp_ms < $tom AND" else ""
                val query = "WHERE$keyQuery$valueQuery$fomQuery$tomQuery"
                query.removeSuffix(" AND").removeSuffix(" ")
            } else ""

            val sql = """
                SELECT * FROM ${table.name} 
                $whereClause
                ORDER BY timestamp_ms DESC 
                LIMIT $limit 
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        suspend fun findAll(
            channels: List<Channel>,
            limit: Int,
            key: List<String>? = null,
            value: List<String>? = null,
            fom: Long? = null,
            tom: Long? = null,
        ): List<Dao> {
            val whereClause = if (key != null || value != null || fom != null || tom != null) {
                val keyQuery =
                    if (key != null) " (" + key.joinToString(" OR ") { "record_key like '%$it%'" } + ") AND" else ""
                val valueQuery =
                    if (value != null) " (" + value.joinToString(" OR ") { "record_value like '%$it%'" } + ") AND" else ""
                val fomQuery = if (fom != null) " timestamp_ms > $fom AND" else ""
                val tomQuery = if (tom != null) " timestamp_ms < $tom AND" else ""
                val query = "WHERE$keyQuery$valueQuery$fomQuery$tomQuery"
                query.removeSuffix(" AND").removeSuffix(" ")
            } else ""

            val unionQuery = channels.joinToString(" UNION ALL ") { channel ->
                "SELECT * FROM ${channel.table.name} $whereClause"
            }

            val sql = """
                SELECT * FROM (
                    $unionQuery
                ) data
                ORDER BY timestamp_ms DESC 
                LIMIT $limit 
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        suspend fun findOppdrag(sakId: String, fagsystem: String): List<Dao> {
            val sql = """
                SELECT *
                FROM oppdrag
                WHERE
                    (xpath(
                            '//ns2:oppdrag/oppdrag-110/fagsystemId/text()',
                            record_value::xml,
                            ARRAY[ARRAY['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]
                     ))[1]::text = '$sakId'
                  AND
                    (xpath(
                            '//ns2:oppdrag/oppdrag-110/kodeFagomraade/text()',
                            record_value::xml,
                            ARRAY[ARRAY['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]
                     ))[1]::text = '$fagsystem';
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery()?.map(::from) ?: emptyList()
            }
        }

        suspend fun findKvitteringer(sakId: String, fagsystem: String): List<Dao> {
            val sql = """
                SELECT *
                FROM kvittering
                WHERE
                    (xpath(
                            '//ns2:oppdrag/oppdrag-110/fagsystemId/text()',
                            record_value::xml,
                            ARRAY[ARRAY['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]
                     ))[1]::text = '$sakId'
                  AND
                    (xpath(
                            '//ns2:oppdrag/oppdrag-110/kodeFagomraade/text()',
                            record_value::xml,
                            ARRAY[ARRAY['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]
                     ))[1]::text = '$fagsystem';
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        suspend fun findUtbetalinger(sakId: String, fagsystem: String): List<Dao> {
            val sql = """
                SELECT *
                FROM utbetalinger
                WHERE json(record_value) ->> 'fagsystem' = '$fagsystem' AND json(record_value) ->> 'sakId' = '$sakId';
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        suspend fun findSimuleringer(sakId: String, fagsystem: String): List<Dao> {
            val sql = """
                SELECT *
                FROM simuleringer
                WHERE json(record_value) ->> 'fagsystem' = '$fagsystem' AND json(record_value) ->> 'sakId' = '$sakId';
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }
    }

    suspend fun insert(table: Table) {
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
                trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        coroutineContext.connection.prepareStatement(sql).use { stmt ->
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
            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }
}

private fun from(rs: ResultSet) = Dao(
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
)

