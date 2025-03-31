package peisstchappern.jdbc

import java.sql.ResultSet
import kotlin.coroutines.coroutineContext
import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.logger
import libs.utils.secureLog

private const val TABLE_NAME = "oppdrag"

private val daoLog = logger("dao")

data class OppdragDao(
    val version: String,
    val topic_name: String,
    val key: String,
    val value: String?,
    val partition: Int,
    val offset: Long,
    val timestamp_ms: Long,
    val stream_time_ms: Long,
    val system_time_ms: Long,
) {
    companion object {
        suspend fun find(key: String, limit: Int = 1000): List<OppdragDao> {
            val sql = """
                SELECT * FROM $TABLE_NAME 
                WHERE key = ? 
                ORDER BY timestamp_ms DESC 
                LIMIT $limit 
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::oppdragDao)
            }
        }
    }

    suspend fun insert() {
        val sql = """
            INSERT INTO $TABLE_NAME (
                version,
                topic_name,
                key,
                value,
                partition,
                offset,
                timestamp_ms,
                stream_time_ms,
                system_time_ms,
            )
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
            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }
}

private fun oppdragDao(rs: ResultSet) = OppdragDao(
    version = rs.getString("version"),
    topic_name = rs.getString("topic_name"),
    key = rs.getString("key"),
    value = rs.getString("value"),
    partition = rs.getInt("partition"),
    offset = rs.getLong("offset"),
    timestamp_ms = rs.getLong("timestamp_ms"),
    stream_time_ms = rs.getLong("stream_time_ms"),
    system_time_ms = rs.getLong("system_time_ms"),
)

