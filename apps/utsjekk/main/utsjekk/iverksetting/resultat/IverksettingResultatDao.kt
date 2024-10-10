package utsjekk.iverksetting.resultat

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import utsjekk.appLog
import utsjekk.iverksetting.*
import java.sql.ResultSet
import kotlin.coroutines.coroutineContext

data class IverksettingResultatDao(
    val fagsystem: Fagsystem,
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val iverksettingId: IverksettingId? = null,
    val tilkjentYtelseForUtbetaling: TilkjentYtelse? = null,
    val oppdragResultat: OppdragResultat? = null,
) {
    suspend fun insert() {
        val sql = """
            INSERT INTO $TABLE_NAME (fagsystem, sakId, behandling_id, iverksetting_id, tilkjentytelseforutbetaling, oppdragresultat )
            VALUES (?,?,?,?,to_json(?::json),to_json(?::json))
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, fagsystem.name)
            stmt.setString(2, sakId.id)
            stmt.setString(3, behandlingId.id)
            stmt.setString(4, iverksettingId?.id)
            stmt.setString(5, objectMapper.writeValueAsString(tilkjentYtelseForUtbetaling))
            stmt.setString(6, objectMapper.writeValueAsString(oppdragResultat))

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    suspend fun update() {

        suspend fun updateWithIverksettingId() {

            val sql = """
                UPDATE $TABLE_NAME 
                SET tilkjentytelseforutbetaling = to_json(?::json), oppdragresultat = to_json(?::json)
                WHERE behandling_id = ? AND sakId = ? AND fagsystem = ? AND iverksetting_id = ?
            """.trimIndent()

            coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, objectMapper.writeValueAsString(tilkjentYtelseForUtbetaling))
                stmt.setString(2, objectMapper.writeValueAsString(oppdragResultat))
                stmt.setString(3, behandlingId.id)
                stmt.setString(4, sakId.id)
                stmt.setString(5, fagsystem.name)
                stmt.setString(6, requireNotNull(iverksettingId).id)

                appLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }

        suspend fun updateWithoutIverksettingId() {
            val sql = """
                UPDATE $TABLE_NAME 
                SET tilkjentytelseforutbetaling = to_json(?::json), oppdragresultat = to_json(?::json)
                WHERE behandling_id = ? AND sakId = ? AND fagsystem = ? AND iverksetting_id IS NULL
            """.trimIndent()

            coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, objectMapper.writeValueAsString(tilkjentYtelseForUtbetaling))
                stmt.setString(2, objectMapper.writeValueAsString(oppdragResultat))
                stmt.setString(3, behandlingId.id)
                stmt.setString(4, sakId.id)
                stmt.setString(5, fagsystem.name)

                appLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeUpdate()
            }
        }

        when (iverksettingId) {
            null -> updateWithoutIverksettingId()
            else -> updateWithIverksettingId()
        }
    }

    companion object {
        const val TABLE_NAME = "iverksettingsresultat"

        suspend fun select(
            limit: Int? = null,
            where: Where.() -> Unit = { Where() },
        ): List<IverksettingResultatDao> {
            val where = Where().apply(where)

            val sql = buildString {
                append("SELECT * FROM $TABLE_NAME")

                if (where.any()) {
                    append(" WHERE ")
                    where.fagsystem?.let { append("fagsystem = ? AND ") }
                    where.sakId?.let { append("sakId = ? AND ") }
                    where.behandlingId?.let { append("behandling_id = ? AND ") }
                    where.iverksettingId?.let { append("iverksetting_id = ? AND ") }
                    where.tilkjentytelseforutbetaling?.let { append("tilkjentytelseforutbetaling = to_json(?::json) AND ") }
                    where.oppdragresultat?.let { append("oppdragresultat = to_json(?::json) AND ") }

                    setLength(length - 4) // Remove dangling "AND "
                }

                limit?.let { append(" LIMIT ?") }
            }

            // The posistion of the question marks in the sql must be relative to the position in the statement
            var position = 1

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                where.fagsystem?.let { stmt.setString(position++, it.name) }
                where.sakId?.let { stmt.setString(position++, it.id) }
                where.behandlingId?.let { stmt.setString(position++, it.id) }
                where.iverksettingId?.let { stmt.setObject(position++, it.id) }
                where.tilkjentytelseforutbetaling?.let { stmt.setString(position++, it.toJson()) }
                where.oppdragresultat?.let { stmt.setString(position++, it.toJson()) }
                limit?.let { stmt.setInt(position++, it) }

                appLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        private fun from(rs: ResultSet) = IverksettingResultatDao(
            fagsystem = Fagsystem.valueOf(rs.getString("fagsystem")),
            sakId = SakId(rs.getString("sakId")),
            behandlingId = BehandlingId(rs.getString("behandling_id")),
            iverksettingId = rs.getString("iverksetting_id")?.let(::IverksettingId),
            tilkjentYtelseForUtbetaling = rs.getString("tilkjentytelseforutbetaling")?.let(TilkjentYtelse::from),
            oppdragResultat = rs.getString("oppdragresultat")?.let(OppdragResultat::from),
        )
    }

    data class Where(
        var fagsystem: Fagsystem? = null,
        var sakId: SakId? = null,
        var behandlingId: BehandlingId? = null,
        var iverksettingId: IverksettingId? = null,
        var tilkjentytelseforutbetaling: TilkjentYtelse? = null,
        var oppdragresultat: OppdragResultat? = null,
    ) {
        fun any() = listOf(
            fagsystem, sakId, behandlingId, iverksettingId, tilkjentytelseforutbetaling, oppdragresultat
        ).any { it != null }
    }
}