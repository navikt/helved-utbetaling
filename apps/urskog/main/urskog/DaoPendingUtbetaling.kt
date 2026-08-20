package urskog

import libs.jdbc.Dao
import libs.utils.jdbcLog
import libs.utils.sha256
import models.Action
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

private val mapper = libs.xml.XMLMapper<Oppdrag>()

data class DaoPendingUtbetaling (
    val hashKey: String,
    val uid: String,
    val mottatt: Boolean = true,
    val mottattAt: LocalDateTime = LocalDateTime.now(),
    val action: Action = Action.CREATE,
) {
    companion object: Dao<DaoPendingUtbetaling> {
        override val table = "pending_utbetaling"

        override fun from(rs: ResultSet) = DaoPendingUtbetaling(
            hashKey = rs.getString("hash_key"),
            uid = rs.getString("uid"),
            mottatt = rs.getBoolean("mottatt"),
            mottattAt = rs.getTimestamp("mottatt_at").toLocalDateTime(),
            action = Action.valueOf(rs.getString("action")),
        )

        fun hash(oppdrag: Oppdrag): String = mapper.writeValueAsString(oppdrag).sha256()

        suspend fun findAll(hashKey: String): List<DaoPendingUtbetaling> {
            val sql = """
                SELECT * FROM $table 
                WHERE hash_key = ? 
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setString(1, hashKey)
            }
        }
    }

    suspend fun insertIdempotent() {
        val sql = """
            INSERT INTO $table (
                hash_key,
                uid,
                mottatt,
                mottatt_at,
                action
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (hash_key, uid) DO NOTHING
        """.trimIndent()

        val rowsAffected = update(sql) { stmt ->
            stmt.setString(1, hashKey)
            stmt.setString(2, uid)
            stmt.setBoolean(3, mottatt)
            stmt.setTimestamp(4, Timestamp.valueOf(mottattAt))
            stmt.setString(5, action.name)
        }

        when(rowsAffected) {
            0 -> jdbcLog.info("Idempotent guard: row in $table already exists for $hashKey/$uid.")
            else -> jdbcLog.info("row in $table inserted for $hashKey/$uid.")
        }
    }
}
