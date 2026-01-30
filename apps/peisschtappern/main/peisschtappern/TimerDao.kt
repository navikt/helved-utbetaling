package peisschtappern

import kotlinx.coroutines.currentCoroutineContext
import libs.jdbc.Dao
import libs.jdbc.concurrency.connection
import libs.jdbc.map
import libs.utils.jdbcLog
import libs.utils.logger
import libs.utils.secureLog
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

private val timeDaoLog = logger("dao")

data class TimerDao(
    val key: String,
    val timeout: LocalDateTime,
    val sakId: String,
    val fagsystem: String,
) {
    companion object: Dao<TimerDao> {
        override val table = "timer"

        override fun from(rs: ResultSet) = TimerDao(
            key = rs.getString("record_key"),
            timeout = rs.getTimestamp("timeout").toLocalDateTime(),
            sakId = rs.getString("sak_id"),
            fagsystem = rs.getString("fagsystem")
        )

        suspend fun find(key: String): TimerDao? {
            val sql = """
                SELECT *
                FROM $table
                WHERE record_key = ?
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setString(1, key)
                timeDaoLog.debug(sql)
                secureLog.debug(stmt.toString())
            }.singleOrNull()
        }

        suspend fun findAll(): List<TimerDao> {
            val sql = """
                SELECT *
                FROM $table
            """.trimIndent()

            return query(sql)
        }

        suspend fun exists(key: String): Boolean {
            val sql = """
                SELECT 1
                FROM $table
                WHERE record_key = ?
                LIMIT 1
            """.trimIndent()

            return statement(sql).use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().next()
            }
        }

        suspend fun delete(key: String): Int {
            val sql = """
                DELETE FROM $table
                WHERE record_key = ?
            """.trimIndent()

            return update(sql) { stmt ->
                stmt.setString(1, key)
            }
        }
    }

    suspend fun insert(): Int {
        val sql = """
            INSERT INTO $table (
            record_key,
            timeout,
            sak_id,
            fagsystem
            ) VALUES (?, ?, ?, ?)
            """.trimIndent()

        return update(sql) { stmt ->
            stmt.setString(1, key)
            stmt.setTimestamp(2, Timestamp.valueOf(timeout))
            stmt.setString(3, sakId)
            stmt.setString(4, fagsystem)
        }
    }
}

