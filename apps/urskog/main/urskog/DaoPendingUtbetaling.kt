package urskog

import kotlinx.coroutines.currentCoroutineContext
import libs.jdbc.concurrency.connection
import libs.jdbc.map
import libs.utils.logger
import libs.utils.secureLog
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

private val daoLog = logger("dao")
private val mapper = libs.xml.XMLMapper<Oppdrag>()

data class DaoPendingUtbetaling (
    val hashKey: Int,
    val uid: String,
    val mottatt: Boolean = true,
    val mottattAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val TABLE = "pending_utbetaling"

        fun hash(oppdrag: Oppdrag): Int { 
            return mapper.writeValueAsString(oppdrag).hashCode()
        }

        suspend fun findAll(hashKey: Int): List<DaoPendingUtbetaling> {
            val sql = """
                SELECT * FROM $TABLE 
                WHERE hash_key = ? 
            """.trimIndent()

            return currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, hashKey)
                daoLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }
    }

    suspend fun insertIdempotent() {
        val sql = """
            INSERT INTO $TABLE (
                hash_key,
                uid,
                mottatt,
                mottatt_at
            ) VALUES (?, ?, ?, ?)
            ON CONFLICT (hash_key, uid) DO NOTHING
        """.trimIndent()

        currentCoroutineContext().connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, hashKey)
            stmt.setString(2, uid)
            stmt.setBoolean(3, mottatt)
            stmt.setTimestamp(4, Timestamp.valueOf(mottattAt))
            daoLog.debug(sql)
            secureLog.debug(stmt.toString())
            when(val rowsAffected = stmt.executeUpdate()) {
                0 -> daoLog.info("Idempotent guard: row in $TABLE already exists for $hashKey/$uid.")
                else -> daoLog.info("row in $TABLE inserted for $hashKey/$uid.")
            }
        }
    }
}

private fun from(rs: ResultSet) = DaoPendingUtbetaling(
    hashKey = rs.getInt("hash_key"),
    uid = rs.getString("uid"),
    mottatt = rs.getBoolean("mottatt"),
    mottattAt = rs.getTimestamp("mottatt_at").toLocalDateTime(),
)
