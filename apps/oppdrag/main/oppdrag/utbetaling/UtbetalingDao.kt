package oppdrag.utbetaling

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.secureLog
import libs.utils.logger
import no.nav.utsjekk.kontrakter.felles.objectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.coroutineContext
import no.trygdeetaten.skjema.oppdrag.Oppdrag

private val daoLog = logger("dao")


data class UtbetalingDao(
    val data: Oppdrag,
    val sakId: String,
    val behandlingId: String,
    val personident: String,
    val klassekode: String,
    val created_at: LocalDateTime = LocalDateTime.now(),
    val updated_at: LocalDateTime = created_at,
) {
    suspend fun insert(id: UtbetalingId) {
        val sql = """
            INSERT INTO $TABLE_NAME (
                id,
                utbetaling_id,
                sak_id, 
                behandling_id, 
                personident, 
                klassekode,
                created_at,
                updated_at,
                data
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, to_json(?::json))
        """.trimIndent()

        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, id.id)
            stmt.setString(3, sakId)
            stmt.setString(4, behandlingId)
            stmt.setString(5, personident)
            stmt.setString(6, klassekode)
            stmt.setTimestamp(7, Timestamp.valueOf(created_at))
            stmt.setTimestamp(8, Timestamp.valueOf(updated_at))
            stmt.setString(9, objectMapper.writeValueAsString(data))

            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    // TODO: create history
    suspend fun update(id: UtbetalingId) {
        val sql = """
            UPDATE $TABLE_NAME
            SET data = to_json(?::json), updated_at = ?
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
        const val TABLE_NAME = "utbetalingsoppdrag"

        suspend fun find(id: UtbetalingId): UtbetalingDao {
            val sql = """
                SELECT * FROM $TABLE_NAME
                WHERE utbetaling_id = ?
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt -> 
                stmt.setObject(1, id.id)

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from).single()
            }
        }

        // TODO: create history
        suspend fun delete(id: UtbetalingId) {
            val sql = """
                DELETE FROM $TABLE_NAME
                WHERE utbetaling_id = ?
            """.trimIndent()

            coroutineContext.connection.prepareStatement(sql).use { stmt -> 
                stmt.setObject(1, id.id)

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }

        fun from(rs: ResultSet)= UtbetalingDao(
            data = objectMapper.readValue(rs.getString("data"), Oppdrag::class.java),
            created_at = rs.getTimestamp("created_at").toLocalDateTime(),
            updated_at = rs.getTimestamp("updated_at").toLocalDateTime(),
            sakId= rs.getString("sak_id"),
            behandlingId= rs.getString("behandling_id"),
            personident= rs.getString("personident"),
            klassekode= rs.getString("klassekode"),
        )
    }
}
