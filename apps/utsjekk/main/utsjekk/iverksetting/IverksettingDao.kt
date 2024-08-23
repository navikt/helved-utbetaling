package utsjekk.iverksetting

import libs.postgres.concurrency.connection
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

data class IverksettingDao(
    val behandlingId: String,
    val data: Iverksetting,
    val mottattTidspunkt: LocalDateTime,
) {

    suspend fun insert() {
        val sql = """
            INSERT INTO iverksetting (behandling_id, data, mottatt_tidspunkt) 
            VALUES (:behandlingId, to_json(:data::json), :mottattTidspunkt)
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, behandlingId)
            stmt.setString(2, objectMapper.writeValueAsString(data))
            stmt.setTimestamp(3, Timestamp.valueOf(mottattTidspunkt))

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

}
