package peisschtappern

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlinx.coroutines.currentCoroutineContext
import libs.jdbc.concurrency.connection
import libs.jdbc.jdbcLog
import libs.jdbc.map
import libs.utils.logger
import libs.utils.secureLog
import kotlin.use

private val timeDaoLog = logger("dao")

data class TimerDao(
    val key: String,
    val timeout: LocalDateTime,
    val sakId: String,
    val fagsystem: String,
) {
    companion object {
        const val TABLE_NAME = "timer"

        suspend fun find(key: String): TimerDao? {
            val sql = """
                SELECT *
                FROM $TABLE_NAME
                WHERE record_key = ?
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                timeDaoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from).singleOrNull()
            }
        }

        suspend fun findAll(): List<TimerDao> {
            val sql = """
                SELECT *
                FROM $TABLE_NAME
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                timeDaoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        suspend fun delete(key: String) {
            val sql = """
                DELETE FROM $TABLE_NAME
                WHERE record_key = ?
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)

                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }
    }

    suspend fun insert() {
        val sql = """
            INSERT INTO $TABLE_NAME (
            record_key,
            timeout,
            sak_id,
            fagsystem
            ) VALUES (?, ?, ?, ?)
            """.trimIndent()

        currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, key)
            stmt.setTimestamp(2, Timestamp.valueOf(timeout))
            stmt.setString(3, sakId)
            stmt.setString(4, fagsystem)
            timeDaoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }
}

private fun from(rs: ResultSet) = TimerDao(
    key = rs.getString("record_key"),
    timeout = rs.getTimestamp("timeout").toLocalDateTime(),
    sakId = rs.getString("sak_id"),
    fagsystem = rs.getString("fagsystem")
)