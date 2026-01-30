package utsjekk.iverksetting

import libs.jdbc.Dao
import models.kontrakter.Fagsystem
import models.kontrakter.objectMapper
import utsjekk.utbetaling.UtbetalingId
import java.sql.ResultSet

data class IverksettingResultatDao(
    val fagsystem: Fagsystem,
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val iverksettingId: IverksettingId? = null,
    val tilkjentYtelseForUtbetaling: TilkjentYtelse? = null,
    val oppdragResultat: OppdragResultat? = null,
) {

    companion object : Dao<IverksettingResultatDao> {
        override val table = "iverksettingsresultat"

        override fun from(rs: ResultSet) = IverksettingResultatDao(
            fagsystem = Fagsystem.valueOf(rs.getString("fagsystem")),
            sakId = SakId(rs.getString("sakId")),
            behandlingId = BehandlingId(rs.getString("behandling_id")),
            iverksettingId = rs.getString("iverksetting_id")?.let(::IverksettingId),
            tilkjentYtelseForUtbetaling = rs.getString("tilkjentytelseforutbetaling")?.let{ TilkjentYtelse.from(it) },
            oppdragResultat = rs.getString("oppdragresultat")?.let{ OppdragResultat.from(it) },
        )

        suspend fun select(
            limit: Int? = null,
            where: Where.() -> Unit = { Where() },
        ): List<IverksettingResultatDao> {
            val where = Where().apply(where)

            val sql = buildString {
                append("SELECT * FROM $table")

                if (where.any()) {
                    append(" WHERE ")
                    where.fagsystem?.let { append("fagsystem = ? AND ") }
                    where.sakId?.let { append("sakId = ? AND ") }
                    where.behandlingId?.let { append("behandling_id = ? AND ") }
                    where.iverksettingId?.let { append("iverksetting_id = ? AND ") }
                    where.tilkjentytelseforutbetaling?.let { append("tilkjentytelseforutbetaling = to_json(?::json) AND ") }
                    where.oppdragresultat?.let { append("oppdragresultat = to_json(?::json) AND ") }
                    where.uid?.let { append("utbetaling_id = ? AND ") }

                    setLength(length - 4) // Remove dangling "AND "
                }

                limit?.let { append(" LIMIT ?") }
            }

            // The posistion of the question marks in the sql must be relative to the position in the statement
            var position = 1

            return query(sql) { stmt ->
                where.fagsystem?.let { stmt.setString(position++, it.name) }
                where.sakId?.let { stmt.setString(position++, it.id) }
                where.behandlingId?.let { stmt.setString(position++, it.id) }
                where.iverksettingId?.let { stmt.setObject(position++, it.id) }
                where.tilkjentytelseforutbetaling?.let { stmt.setString(position++, it.toJson()) }
                where.oppdragresultat?.let { stmt.setString(position++, it.toJson()) }
                where.uid?.let { stmt.setObject(position++, it.id) }
                limit?.let { stmt.setInt(position, it) } // position++ if more where parameters is added
            }
        }
    }
    suspend fun insert(uid: UtbetalingId): Int {
        val sql = """
            INSERT INTO $table (fagsystem, sakId, behandling_id, iverksetting_id, tilkjentytelseforutbetaling, oppdragresultat, utbetaling_id)
            VALUES (?,?,?,?,to_json(?::json),to_json(?::json), ?)
        """.trimIndent()

        return update(sql) { stmt ->
            stmt.setObject(1, fagsystem.name)
            stmt.setString(2, sakId.id)
            stmt.setString(3, behandlingId.id)
            stmt.setString(4, iverksettingId?.id)

            tilkjentYtelseForUtbetaling?.let { 
                stmt.setString(5, objectMapper.writeValueAsString(it)) 
            } ?: stmt.setNull(5, java.sql.Types.OTHER)

            oppdragResultat?.let { 
                stmt.setString(6, objectMapper.writeValueAsString(it))
            } ?: stmt.setNull(6, java.sql.Types.OTHER)

            stmt.setObject(7, uid.id)
        }
    }

    suspend fun update(uid: UtbetalingId): Int {
        val sql = """
                UPDATE $table 
                SET tilkjentytelseforutbetaling = to_json(?::json), oppdragresultat = to_json(?::json)
                WHERE utbetaling_id = ?
        """.trimIndent()

        return update(sql) { stmt ->
            tilkjentYtelseForUtbetaling?.let { 
                stmt.setString(1, objectMapper.writeValueAsString(it)) 
            } ?: stmt.setNull(1, java.sql.Types.OTHER)

            oppdragResultat?.let { 
                stmt.setString(2, objectMapper.writeValueAsString(it))
            } ?: stmt.setNull(2, java.sql.Types.OTHER)

            stmt.setObject(3, uid.id)
        }
    }

    suspend fun update() {

        suspend fun updateWithIverksettingId(): Int {
            val sql = """
                UPDATE $table 
                SET tilkjentytelseforutbetaling = to_json(?::json), oppdragresultat = to_json(?::json)
                WHERE behandling_id = ? AND sakId = ? AND fagsystem = ? AND iverksetting_id = ?
            """.trimIndent()

            return update(sql) { stmt -> 
                tilkjentYtelseForUtbetaling?.let { 
                    stmt.setString(1, objectMapper.writeValueAsString(it)) 
                } ?: stmt.setNull(1, java.sql.Types.OTHER)

                oppdragResultat?.let { 
                    stmt.setString(2, objectMapper.writeValueAsString(it))
                } ?: stmt.setNull(2, java.sql.Types.OTHER)

                stmt.setString(3, behandlingId.id)
                stmt.setString(4, sakId.id)
                stmt.setString(5, fagsystem.name)
                stmt.setString(6, requireNotNull(iverksettingId).id)
            }
        }

        suspend fun updateWithoutIverksettingId(): Int {
            val sql = """
                UPDATE $table 
                SET tilkjentytelseforutbetaling = to_json(?::json), oppdragresultat = to_json(?::json)
                WHERE behandling_id = ? AND sakId = ? AND fagsystem = ? AND iverksetting_id IS NULL
            """.trimIndent()

            return update(sql) { stmt ->
                tilkjentYtelseForUtbetaling?.let { 
                    stmt.setString(1, objectMapper.writeValueAsString(it)) 
                } ?: stmt.setNull(1, java.sql.Types.OTHER)

                oppdragResultat?.let { 
                    stmt.setString(2, objectMapper.writeValueAsString(it))
                } ?: stmt.setNull(2, java.sql.Types.OTHER)

                stmt.setString(3, behandlingId.id)
                stmt.setString(4, sakId.id)
                stmt.setString(5, fagsystem.name)
            }
        }

        when (iverksettingId) {
            null -> updateWithoutIverksettingId()
            else -> updateWithIverksettingId()
        }
    }

    data class Where(
        var fagsystem: Fagsystem? = null,
        var sakId: SakId? = null,
        var behandlingId: BehandlingId? = null,
        var iverksettingId: IverksettingId? = null,
        var tilkjentytelseforutbetaling: TilkjentYtelse? = null,
        var oppdragresultat: OppdragResultat? = null,
        var uid: UtbetalingId? = null,
    ) {
        fun any() = listOf(
            fagsystem,
            sakId,
            behandlingId,
            iverksettingId,
            tilkjentytelseforutbetaling,
            oppdragresultat,
            uid
        ).any { it != null }
    }
}
