package utsjekk.iverksetting

import libs.postgres.concurrency.connection
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

data class IverksettingDao(
    val behandlingId: BehandlingId,
    val data: Iverksetting,
    val mottattTidspunkt: LocalDateTime,
) {

    suspend fun insert() {
        val sql = """
            INSERT INTO iverksetting (behandling_id, data, mottatt_tidspunkt) 
            VALUES (?, to_json(?::json), ?)
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, behandlingId.id) // todo: try behandlingId without .id
            stmt.setString(2, objectMapper.writeValueAsString(data))
            stmt.setTimestamp(3, Timestamp.valueOf(mottattTidspunkt))

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

}
