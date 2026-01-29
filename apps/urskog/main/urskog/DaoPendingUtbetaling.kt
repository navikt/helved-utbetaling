package urskog

import libs.jdbc.Dao
import libs.utils.logger
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
    companion object: Dao<DaoPendingUtbetaling> {
        override val table = "pending_utbetaling"

        override fun from(rs: ResultSet) = DaoPendingUtbetaling(
            hashKey = rs.getInt("hash_key"),
            uid = rs.getString("uid"),
            mottatt = rs.getBoolean("mottatt"),
            mottattAt = rs.getTimestamp("mottatt_at").toLocalDateTime(),
        )

        fun hash(oppdrag: Oppdrag): Int { 
            return mapper.writeValueAsString(oppdrag).hashCode()
        }

        suspend fun findAll(hashKey: Int): List<DaoPendingUtbetaling> {
            val sql = """
                SELECT * FROM $table 
                WHERE hash_key = ? 
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setInt(1, hashKey)
            }
        }
    }

    suspend fun insertIdempotent() {
        val sql = """
            INSERT INTO $table (
                hash_key,
                uid,
                mottatt,
                mottatt_at
            ) VALUES (?, ?, ?, ?)
            ON CONFLICT (hash_key, uid) DO NOTHING
        """.trimIndent()

        val rowsAffected = update(sql) { stmt ->
            stmt.setInt(1, hashKey)
            stmt.setString(2, uid)
            stmt.setBoolean(3, mottatt)
            stmt.setTimestamp(4, Timestamp.valueOf(mottattAt))
        }

        when(rowsAffected) {
            0 -> daoLog.info("Idempotent guard: row in $table already exists for $hashKey/$uid.")
            else -> daoLog.info("row in $table inserted for $hashKey/$uid.")
        }
    }
}

