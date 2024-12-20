package oppdrag.utbetaling

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.secureLog
import libs.utils.logger
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import oppdrag.iverksetting.tilstand.OppdragId
import java.util.UUID
import no.trygdeetaten.skjema.oppdrag.Mmel
import kotlin.coroutines.coroutineContext
import no.trygdeetaten.skjema.oppdrag.Oppdrag

private val daoLog = logger("dao")


data class UtbetalingDao(
    val uid: UtbetalingId,
    val data: Oppdrag,
    val sakId: String,
    val behandlingId: String,
    val personident: String,
    val klassekode: String,
    val kvittering: Mmel?,
    val fagsystem: String,
    val status: OppdragStatus,
    val created_at: LocalDateTime = LocalDateTime.now(),
    val updated_at: LocalDateTime = created_at,
) {
    suspend fun insert() {
        val sql = """
            INSERT INTO $TABLE_NAME (
                id,
                utbetaling_id,
                sak_id, 
                behandling_id, 
                personident, 
                klassekode,
                kvittering,
                fagsystem,
                status,
                created_at,
                updated_at,
                data
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_json(?::json))
        """.trimIndent()

        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, uid.id)
            stmt.setString(3, sakId)
            stmt.setString(4, behandlingId)
            stmt.setString(5, personident)
            stmt.setString(6, klassekode)
            stmt.setObject(7, fagsystem)
            stmt.setObject(8, status)
            stmt.setTimestamp(9, Timestamp.valueOf(created_at))
            stmt.setTimestamp(10, Timestamp.valueOf(updated_at))
            stmt.setString(11, objectMapper.writeValueAsString(data))
            stmt.setString(12, objectMapper.writeValueAsString(kvittering))

            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    // TODO: create history
    suspend fun update() {
        val sql = """
            UPDATE $TABLE_NAME
            SET 
                data = to_json(?::json),
                updated_at = ?,
                kvittering = to_json(?::json),
                status = ?
            WHERE utbetaling_id = ?
        """.trimIndent()

        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, objectMapper.writeValueAsString(data))
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
            stmt.setString(3, objectMapper.writeValueAsString(kvittering))
            stmt.setObject(4, status)
            stmt.setObject(5, uid.id)

            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    companion object {
        const val TABLE_NAME = "utbetalingsoppdrag"

        suspend fun findOrNull(id: UtbetalingId): UtbetalingDao? {
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

        suspend fun findOrNull(id: OppdragId): List<UtbetalingDao> {
            val sql = """
                SELECT * FROM $TABLE_NAME
                WHERE 
                    sak_id = ?
                AND behandling_id = ?
                AND fagsystem = ?  
            """.trimIndent()

            return coroutineContext.connection.prepareStatement(sql).use { stmt -> 
                stmt.setString(1, id.fagsakId)
                stmt.setString(2, id.behandlingId)
                stmt.setString(3, id.fagsystem.name)

                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
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

        fun from(rs: ResultSet):UtbetalingDao { 
            val kvittering: String? = rs.getString("kvittering")
            return UtbetalingDao(
                uid = UtbetalingId(UUID.fromString(rs.getString("utbetaling_id"))),
                data = objectMapper.readValue(rs.getString("data"), Oppdrag::class.java),
                created_at = rs.getTimestamp("created_at").toLocalDateTime(),
                updated_at = rs.getTimestamp("updated_at").toLocalDateTime(),
                sakId= rs.getString("sak_id"),
                behandlingId= rs.getString("behandling_id"),
                personident= rs.getString("personident"),
                klassekode= rs.getString("klassekode"),
                status = OppdragStatus.valueOf(rs.getString("status")),
                fagsystem = rs.getString("fagsystem"),
                kvittering = kvittering?.let {
                    objectMapper.readValue(it, Mmel::class.java)
                },
            )
        }
    }
}

