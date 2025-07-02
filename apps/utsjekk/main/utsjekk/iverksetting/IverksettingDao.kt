package utsjekk.iverksetting

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext
import libs.jdbc.*
import libs.jdbc.concurrency.connection
import libs.utils.*
import models.kontrakter.felles.Fagsystem
import models.kontrakter.felles.objectMapper
import utsjekk.utbetaling.UtbetalingId

data class IverksettingDao(
    val data: Iverksetting,
    val mottattTidspunkt: LocalDateTime,
) {

    suspend fun insert(uid: UtbetalingId) {
        val sql = """
            INSERT INTO $TABLE_NAME (behandling_id, data, mottatt_tidspunkt, utbetaling_id) 
            VALUES (?, to_json(?::json), ?, ?)
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, data.behandlingId.id)
            stmt.setString(2, objectMapper.writeValueAsString(data))
            stmt.setTimestamp(3, Timestamp.valueOf(mottattTidspunkt))
            stmt.setObject(4, uid.id)

            jdbcLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    companion object {
        const val TABLE_NAME = "iverksetting"

        suspend fun uid(where: Where.() -> Unit = { Where() }) : UtbetalingId? {
            val where = Where().apply(where)

            val sql = buildString {
                append("SELECT utbetaling_id FROM $TABLE_NAME")

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

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                where.behandlingId?.let { stmt.setString(position++, it.id) }
                where.sakId?.let { stmt.setString(position++, it.id) }
                where.iverksettingId?.let { stmt.setString(position++, it.id) }
                where.fagsystem?.let { stmt.setString(position++, it.name) }

                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().mapNotNull { rs ->
                    rs.getString("utbetaling_id")?.let { UtbetalingId(UUID.fromString(it)) }
                }.singleOrNull()
            }
        }

        private fun <T : Any> ResultSet.mapNotNull(block: (ResultSet) -> T?): List<T> =
            sequence { while (next()) yield(block(this@mapNotNull)) }.toList().filterNotNull()

        suspend fun select(
            limit: Int? = null,
            where: Where.() -> Unit = { Where() },
        ): List<IverksettingDao> {
            val where = Where().apply(where)

            val sql = buildString {
                append("SELECT * FROM $TABLE_NAME")

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

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                where.behandlingId?.let { stmt.setString(position++, it.id) }
                where.sakId?.let { stmt.setString(position++, it.id) }
                where.iverksettingId?.let { stmt.setString(position++, it.id) }
                where.fagsystem?.let { stmt.setString(position++, it.name) }
                limit?.let { stmt.setInt(position++, it) }

                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        fun from(rs: ResultSet) = IverksettingDao(
            data = objectMapper.readValue(rs.getString("data"), Iverksetting::class.java),
            mottattTidspunkt = rs.getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
        )
    }

    data class Where(
        var behandlingId: BehandlingId? = null,
        var sakId: SakId? = null,
        var iverksettingId: IverksettingId? = null,
        var fagsystem: Fagsystem? = null,
    ) {
        fun any() = listOf(
            sakId, behandlingId, iverksettingId, fagsystem
        ).any { it != null }
    }
}
