package utsjekk.iverksetting

import libs.jdbc.Dao
import libs.kotlinx.KotlinxJson
import models.kontrakter.Fagsystem
import utsjekk.utbetaling.UtbetalingId
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

data class IverksettingDao(
    val data: Iverksetting,
    val mottattTidspunkt: LocalDateTime,
) {
    companion object: Dao<IverksettingDao> {
        override val table = "iverksetting"

        override fun from(rs: ResultSet) = IverksettingDao(
            data = KotlinxJson.decodeFromString(rs.getString("data")),
            mottattTidspunkt = rs.getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
        )

        suspend fun uid(where: Where.() -> Unit = { Where() }) : UtbetalingId? {
            val where = Where().apply(where)

            val sql = buildString {
                append("SELECT utbetaling_id FROM $table")

                if (where.any()) {
                    append(" WHERE ")
                    where.behandlingId?.let { append("behandling_id = ? AND ") }
                    where.sakId?.let { append("data -> 'fagsak' ->> 'fagsakId' = ? AND ") }
                    where.iverksettingId?.let { append("data -> 'behandling' ->> 'iverksettingId' = ? AND ") }
                    where.fagsystem?.let { append("data -> 'fagsak' ->> 'fagsystem' = ? AND ") }
                    setLength(length - 4) // Remove dangling "AND "
                }
            }

            // The posistion of the question marks in the sql must be relative to the position in the statement
            var position = 1

            fun toUid(rs: ResultSet) = rs.getString("utbetaling_id")?.let { UtbetalingId(UUID.fromString(it)) }

            return query(sql, mapper = ::toUid) { stmt ->
                where.behandlingId?.let { stmt.setString(position++, it.id) }
                where.sakId?.let { stmt.setString(position++, it.id) }
                where.iverksettingId?.let { stmt.setString(position++, it.id) }
                where.fagsystem?.let { stmt.setString(position, it.name) } // position++ if more where parameters is added
            }.singleOrNull()
        }

        suspend fun select(
            limit: Int? = null,
            where: Where.() -> Unit = { Where() },
        ): List<IverksettingDao> {
            val where = Where().apply(where)

            val sql = buildString {
                append("SELECT * FROM $table")

                if (where.any()) {
                    append(" WHERE ")
                    where.behandlingId?.let { append("behandling_id = ? AND ") }
                    where.sakId?.let { append("data -> 'fagsak' ->> 'fagsakId' = ? AND ") }
                    where.iverksettingId?.let { append("data -> 'behandling' ->> 'iverksettingId' = ? AND ") }
                    where.fagsystem?.let { append("data -> 'fagsak' ->> 'fagsystem' = ? AND ") }

                    setLength(length - 4) // Remove dangling "AND "
                }

                limit?.let { append(" LIMIT ?") }
            }

            // The posistion of the question marks in the sql must be relative to the position in the statement
            var position = 1

            return query(sql) { stmt ->
                where.behandlingId?.let { stmt.setString(position++, it.id) }
                where.sakId?.let { stmt.setString(position++, it.id) }
                where.iverksettingId?.let { stmt.setString(position++, it.id) }
                where.fagsystem?.let { stmt.setString(position++, it.name) }
                limit?.let { stmt.setInt(position, it) } // position++ if more where parameters is added
            }
        }

        /**
         * Markerer alle iverksettinger på saken som migrert. Låser radene, så samtidige kall venter.
         * @return false om saken allerede er migrert
         */
        suspend fun markMigrated(sakId: SakId, fagsystem: Fagsystem): Boolean {
            val sql = """
                UPDATE $table
                SET migrated_at = ?
                WHERE data -> 'fagsak' ->> 'fagsakId' = ? 
                  AND data -> 'fagsak' ->> 'fagsystem' = ?
                  AND migrated_at IS NULL
            """.trimIndent()

            return update(sql) { stmt ->
                stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
                stmt.setString(2, sakId.id)
                stmt.setString(3, fagsystem.name)
            } > 0
        }

        suspend fun isMigrated(sakId: SakId, fagsystem: Fagsystem): Boolean {
            val sql = """
                SELECT 1 FROM $table
                WHERE data -> 'fagsak' ->> 'fagsakId' = ? 
                  AND data -> 'fagsak' ->> 'fagsystem' = ?
                  AND migrated_at IS NOT NULL
                LIMIT 1
            """.trimIndent()

            return query(sql, mapper = { true }) { stmt ->
                stmt.setString(1, sakId.id)
                stmt.setString(2, fagsystem.name)
            }.isNotEmpty()
        }
    }

    suspend fun insert(uid: UtbetalingId): Int {
        val sql = """
            INSERT INTO $table (behandling_id, data, mottatt_tidspunkt, utbetaling_id) 
            VALUES (?, to_json(?::json), ?, ?)
        """.trimIndent()

        return update(sql) { stmt ->
            stmt.setObject(1, data.behandlingId.id)
            stmt.setString(2, KotlinxJson.encodeToString(data))
            stmt.setTimestamp(3, Timestamp.valueOf(mottattTidspunkt))
            stmt.setObject(4, uid.id)
        }
    }

    data class Where(
        var behandlingId: BehandlingId? = null,
        var sakId: SakId? = null,
        var iverksettingId: IverksettingId? = null,
        var fagsystem: Fagsystem? = null,
    ) {
        fun any() = listOf(
            sakId,
            behandlingId,
            iverksettingId,
            fagsystem
        ).any { it != null }
    }
}
