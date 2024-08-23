package utsjekk.iverksetting

import libs.postgres.concurrency.connection
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

const val TABLE_NAME = "iverksettingsresultat"

data class IverksettingResultatDao(
    val fagsystem: Fagsystem,
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String? = null,
    val tilkjentytelseforutbetaling: TilkjentYtelse? = null,
    val oppdragresultat: OppdragResultat? = null,
) {
    suspend fun insert() {
        val sql = """
            INSERT INTO $TABLE_NAME (fagsystem, sakId, behandling_id, iverksetting_id, tilkjentytelseforutbetaling)
            VALUES (?,?,?,?,to_json(?::json))
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, fagsystem.name)
            stmt.setString(2, sakId)
            stmt.setString(3, behandlingId)
            stmt.setString(4, iverksettingId)
            stmt.setString(5, objectMapper.writeValueAsString(tilkjentytelseforutbetaling))

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }
}

data class OppdragResultat(
    val oppdragStatus: OppdragStatus,
    val oppdragStatusOppdatert: LocalDateTime = LocalDateTime.now(),
)
