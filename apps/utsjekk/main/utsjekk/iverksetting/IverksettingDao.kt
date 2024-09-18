package utsjekk.iverksetting

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

data class IverksettingDao(
    val behandlingId: BehandlingId, // todo: denne kan erstattes med data.behandlingId?
    val data: Iverksetting,
    val mottattTidspunkt: LocalDateTime,
) {

    suspend fun insert() {
        val sql = """
            INSERT INTO $TABLE_NAME (behandling_id, data, mottatt_tidspunkt) 
            VALUES (?, to_json(?::json), ?)
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, behandlingId.id)
            stmt.setString(2, objectMapper.writeValueAsString(data))
            stmt.setTimestamp(3, Timestamp.valueOf(mottattTidspunkt))

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    companion object {
        const val TABLE_NAME = "iverksetting"

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

                appLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        fun from(rs: ResultSet) = IverksettingDao(
            behandlingId = BehandlingId(rs.getString("behandling_id")),
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
