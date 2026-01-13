package abetal

import libs.jdbc.concurrency.connection
import libs.jdbc.map
import libs.utils.logger
import libs.utils.secureLog
import java.sql.ResultSet
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import kotlinx.coroutines.currentCoroutineContext
import java.util.UUID

private val daoLog = logger("dao")

data class DaoFks (
    val uids: List<String>,
) {
    companion object {
        const val TABLE = "fks"

        private val mapper: libs.xml.XMLMapper<Oppdrag> = libs.xml.XMLMapper()

        fun hash(oppdrag: Oppdrag): Int {
            return mapper.writeValueAsString(oppdrag).hashCode()
        }

        suspend fun firstOrNull(hashKey: Int): DaoFks? {
            val sql = """
                SELECT * FROM $TABLE 
                WHERE hash_key = ? 
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, hashKey)
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from).firstOrNull()
            }
        }
    }

    suspend fun insert(oppdrag: Oppdrag) {
        val sql = """
            INSERT INTO $TABLE (
                hash_key,
                uids
            ) VALUES (?, ?)
        """.trimIndent()

        currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, hash(oppdrag))
            stmt.setString(2, uids.joinToString(","))
            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }
}

private fun from(rs: ResultSet) = DaoFks(
    uids = rs.getString("uids").split(","),
)
