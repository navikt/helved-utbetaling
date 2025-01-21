package utsjekk.utbetaling

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.Result
import libs.utils.logger
import libs.utils.map
import libs.utils.mapErr
import utsjekk.utbetaling.*
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import org.intellij.lang.annotations.Language
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.coroutineContext

// enum class DaoError {
private val daoLog = logger("dao")

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
            coroutineContext.connection.prepareStatement(sql).use { stmt ->
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

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }
            .map { Unit }
            .mapErr { DatabaseError.Unknown }
    }

    // TODO: create history
    suspend fun update(id: UtbetalingId): Result<Unit, DatabaseError> {
        val sql = """
            UPDATE $TABLE_NAME
            SET data = ?::jsonb, updated_at = ?
            WHERE utbetaling_id = ?
        """.trimIndent()

        return tryResult {
            coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, objectMapper.writeValueAsString(data))
                stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
                stmt.setObject(3, id.id)

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }
            .map { Unit }
            .mapErr { DatabaseError.Unknown }
    }

    companion object {
        const val TABLE_NAME = "utbetaling"

        suspend fun findOrNull(id: UtbetalingId, withHistory: Boolean = false): UtbetalingDao? {
            val sql = when (withHistory) {
                true -> """
                    SELECT * FROM $TABLE_NAME
                    WHERE utbetaling_id = ?;
                """.trimIndent()
                false -> """
                    SELECT * FROM $TABLE_NAME
                    WHERE utbetaling_id = ? AND deleted_at IS null;
                """.trimIndent()
            }

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, id.id)

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from).singleOrNull()
            }
        }

        // todo: Finner utbetalinger som er slettet. Mulig vi må endre på dette.
        suspend fun find(sakId: SakId): List<UtbetalingDao> {
            val sql = """
                SELECT * FROM $TABLE_NAME
                WHERE sak_id = ?
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, sakId.id)
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }


        // TODO: create history
        suspend fun delete(id: UtbetalingId): Result<Unit, DatabaseError> {
            val sql = """
                UPDATE $TABLE_NAME
                SET deleted_at = ?
                WHERE utbetaling_id = ?
            """.trimIndent()

            return tryResult {
                coroutineContext.connection.prepareStatement(sql).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
                    stmt.setObject(2, id.id)

                    daoLog.debug(sql)
                    secureLog.debug(stmt.toString())
                    stmt.executeUpdate()
                }
            }
                .map { Unit }
                .mapErr { DatabaseError.Unknown }
        }

        fun from(rs: ResultSet) = UtbetalingDao(
            data = objectMapper.readValue(rs.getString("data"), Utbetaling::class.java),
            stønad = rs.getString("stønad").let(Stønadstype::valueOf),
            status = rs.getString("status").let(Status::valueOf),
            created_at = rs.getTimestamp("created_at").toLocalDateTime(),
            updated_at = rs.getTimestamp("updated_at").toLocalDateTime(),
        )
    }
}
