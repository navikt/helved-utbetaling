package utsjekk.utbetaling

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.secureLog
import libs.utils.logger
import no.nav.utsjekk.kontrakter.felles.objectMapper
import libs.utils.Result
import utsjekk.appLog
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.coroutineContext

private val daoLog = logger("dao")

data class UtbetalingStatusDao(
    val data: UtbetalingStatus,
    val created_at: LocalDateTime = LocalDateTime.now(),
    val updated_at: LocalDateTime = created_at,
) {

    suspend fun insert(id: UtbetalingId) {
        val sql = """
            INSERT INTO $TABLE_NAME (
                id,
                utbetaling_id,
                created_at,
                updated_at,
                status
            ) VALUES (?, ?, ?, ?, to_json(?::json))
        """.trimIndent()

        val now = LocalDateTime.now()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, id.id)
            stmt.setTimestamp(3, Timestamp.valueOf(now))
            stmt.setTimestamp(4, Timestamp.valueOf(now))
            stmt.setString(5, objectMapper.writeValueAsString(data))

            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    // TODO: create history
    suspend fun update(id: UtbetalingId) {
        val sql = """
            UPDATE $TABLE_NAME
            SET status = to_json(?::json), updated_at = ? 
            WHERE utbetaling_id = ?
        """.trimIndent()

        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, objectMapper.writeValueAsString(data))
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
            stmt.setObject(3, id.id)

            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    companion object {
        const val TABLE_NAME = "utbetaling_status"

        suspend fun findOrNull(id: UtbetalingId): UtbetalingStatusDao? {
            val sql = """
                SELECT * FROM $TABLE_NAME
                WHERE utbetaling_id = ?
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt -> 
                stmt.setObject(1, id.id)

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from).singleOrNull()
            }
        }

        // TODO: create history
        suspend fun delete(id: UtbetalingId) {
            val sql = """
                DELETE FROM $TABLE_NAME
                WHERE utbetaling_id = ?
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt -> 
                stmt.setObject(1, id.id)

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }

        fun from(rs: ResultSet) = UtbetalingStatusDao(
            data = objectMapper.readValue(rs.getString("status"), UtbetalingStatus::class.java),
            created_at = rs.getTimestamp("created_at").toLocalDateTime(),
            updated_at = rs.getTimestamp("updated_at").toLocalDateTime(),
        )
    }
}
