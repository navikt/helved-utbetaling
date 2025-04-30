package vedskiva

import java.sql.ResultSet
import java.time.LocalDate
import kotlin.coroutines.coroutineContext
import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.logger
import libs.utils.secureLog

data class Scheduled(
    val created_at: LocalDate,
    val avstemt_fom: LocalDate,
    val avstemt_tom: LocalDate,
) {
    companion object {
        const val TABLE_NAME = "scheduled"

        suspend fun lastOrNull(): Scheduled? {
            val sql = "SELECT * FROM  $TABLE_NAME ORDER BY created_at DESC LIMIT 1"
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                jdbcLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from).singleOrNull()
            }
        }
    }

    suspend fun insert() {
        val sql = "INSERT INTO $TABLE_NAME (created_at, avstemt_fom, avstemt_tom) VALUES (?, ?, ?)"
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, created_at)
            stmt.setObject(2, avstemt_fom)
            stmt.setObject(3, avstemt_tom)
            jdbcLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }
}

private fun from(r: ResultSet) = Scheduled(
    created_at = r.getDate("created_at").toLocalDate(),
    avstemt_fom = r.getDate("avstemt_fom").toLocalDate(),
    avstemt_tom = r.getDate("avstemt_tom").toLocalDate(),
)

private val jdbcLog = logger("jdbc")

