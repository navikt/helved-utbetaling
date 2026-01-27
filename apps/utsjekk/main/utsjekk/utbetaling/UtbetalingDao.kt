package utsjekk.utbetaling

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import libs.jdbc.*
import libs.jdbc.concurrency.connection
import libs.utils.*
import libs.utils.secureLog
import models.kontrakter.objectMapper

enum class DatabaseError {
    Conflict,
    Unknown,
}

suspend fun <T> tryResult(block: suspend () -> T): Result<T, Throwable> {
    return Result.catch { block() }
}

data class UtbetalingDao(
    val data: Utbetaling,
    val status: Status = Status.IKKE_PÅBEGYNT,
    val stønad: Stønadstype = data.stønad,
    val created_at: LocalDateTime = LocalDateTime.now(),
    val updated_at: LocalDateTime = created_at,
    val deleted_at: LocalDateTime? = null,
) {
    suspend fun insert(id: UtbetalingId): Result<Unit, DatabaseError> {
        val sql = """
            INSERT INTO $TABLE_NAME (
                id,
                utbetaling_id,
                sak_id, 
                behandling_id, 
                personident, 
                stønad,
                created_at,
                updated_at,
                data,
                status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """.trimIndent()

        return tryResult {
            currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, id.id)
                stmt.setString(3, data.sakId.id)
                stmt.setString(4, data.behandlingId.id)
                stmt.setString(5, data.personident.ident)
                stmt.setString(6, data.stønad.name)
                stmt.setTimestamp(7, Timestamp.valueOf(created_at))
                stmt.setTimestamp(8, Timestamp.valueOf(updated_at))
                stmt.setString(9, objectMapper.writeValueAsString(data))
                stmt.setString(10, status.name)

                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }
            .map { Unit }
            .mapErr { DatabaseError.Unknown }
    }

    suspend fun update(id: UtbetalingId): Result<Unit, DatabaseError> {
        // inner most select is used to get the latest utbetaling for a given utbetaling_id
        val sql = """
            UPDATE $TABLE_NAME
            SET updated_at = ?, status = ?
            WHERE utbetaling_id = ? AND id IN (
                SELECT id 
                FROM $TABLE_NAME
                WHERE utbetaling_id = ?
                ORDER BY created_at DESC
                LIMIT 1
            )
            AND (deleted_at IS NULL OR status = 'FEILET_MOT_OPPDRAG')
        """.trimIndent()

        return tryResult {
            currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
                stmt.setString(2, status.name)
                stmt.setObject(3, id.id)
                stmt.setObject(4, id.id)

                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }
            .map { Unit }
            .mapErr { DatabaseError.Unknown }
    }

    /**
     * Vi ønsker å markerer utbetalingen (inkl all historikk) som deleted 
     * slik at det gjenspeiler opphøret hos PO Utbetaling.
     */
    suspend fun delete(id: UtbetalingId): Result<Unit, DatabaseError> {
        val sql = """
            UPDATE $TABLE_NAME
            SET deleted_at = ?
            WHERE utbetaling_id = ?
        """.trimIndent()

        return tryResult {
            currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
                stmt.setObject(2, id.id)

                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }
            .map { Unit }
            .mapErr { DatabaseError.Unknown }
    }

    companion object {
        const val TABLE_NAME = "utbetaling"

        suspend fun findOrNull(id: UtbetalingId, history: Boolean = false): UtbetalingDao? {
            val sql = """
                SELECT * FROM $TABLE_NAME
                WHERE utbetaling_id = ?
                ORDER BY created_at DESC
                LIMIT 1
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, id.id)

                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery()
                    .map(::from)
                    .filter { it.deleted_at == null || history }
                    .singleOrNull()
            }
        }

        suspend fun find(sakId: SakId, history: Boolean = false): List<UtbetalingDao> {
            val sql = """
                SELECT * FROM $TABLE_NAME
                WHERE sak_id = ?
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, sakId.id)
                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery()
                    .map(::from)
                    .filter { it.deleted_at == null || history }
            }
        }

        fun from(rs: ResultSet) = UtbetalingDao(
            data = objectMapper.readValue(rs.getString("data"), Utbetaling::class.java),
            stønad = rs.getString("stønad").let(Stønadstype::valueOf),
            status = rs.getString("status").let(Status::valueOf),
            created_at = rs.getTimestamp("created_at").toLocalDateTime(),
            updated_at = rs.getTimestamp("updated_at").toLocalDateTime(),
            deleted_at = rs.getTimestamp("deleted_at")?.toLocalDateTime(),
        )
    }
}
