package urskog

import libs.jdbc.Dao
import libs.utils.jdbcLog
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

private val mapper = libs.xml.XMLMapper<Oppdrag>()

data class DaoOppdrag (
    val kafkaKey: String,
    val sakId: String,
    val behandlingId: String,
    val oppdrag: Oppdrag,
    val uids: List<String>,
    val sent: Boolean = false,
    val sentAt: LocalDateTime? = null,
) {

    companion object: Dao<DaoOppdrag> {
        override val table = "oppdrag" 

        override fun from(rs: ResultSet) = DaoOppdrag(
            kafkaKey = rs.getString("kafka_key"),
            oppdrag = rs.getString("oppdrag").let { mapper.readValue(it) },
            sakId = rs.getString("sak_id"),
            behandlingId = rs.getString("behandling_id"),
            uids = rs.getString("uids")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            sent = rs.getBoolean("sent"),
            sentAt = rs.getTimestamp("sent_at")?.toLocalDateTime(),
        )

        fun hash(oppdrag: Oppdrag): Int { 
            return mapper.writeValueAsString(oppdrag).hashCode()
        }

        suspend fun find(hashKey: Int): DaoOppdrag? {
            val sql = """
                SELECT * FROM $table 
                WHERE hash_key = ? 
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setInt(1, hashKey)
            }.firstOrNull()
        }

        suspend fun findWithLock(hashKey: Int): DaoOppdrag? {
            val sql = """
                SELECT * FROM $table 
                WHERE hash_key = ? 
                FOR UPDATE
            """.trimIndent()

            return query(sql) { stmt ->
                stmt.setInt(1, hashKey)
            }.firstOrNull()
        }
    }

    suspend fun insertIdempotent(): Boolean {
        val sql = """
            INSERT INTO $table (
                hash_key,
                kafka_key,
                oppdrag,
                sak_id,
                behandling_id,
                uids,
                sent,
                sent_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (hash_key) DO NOTHING
        """.trimIndent()

        val hashKey = hash(oppdrag)

        val rowsAffected = update(sql) { stmt ->
            stmt.setInt(1, hashKey)
            stmt.setString(2, kafkaKey)
            stmt.setString(3, mapper.writeValueAsString(oppdrag))
            stmt.setString(4, sakId)
            stmt.setString(5, behandlingId)
            stmt.setString(6, uids.joinToString(","))
            stmt.setBoolean(7, sent)
            stmt.setTimestamp(8, sentAt?.let { Timestamp.valueOf(it) }) 
        }

        return when(rowsAffected) {
            0 -> false.also { jdbcLog.info("Idempotent guard: row in $table already exists for $hashKey") }
            else -> true.also{ jdbcLog.info("row in $table inserted for $hashKey") }
        }
    }

    suspend fun updateAsSent() {
        val sql = """
            UPDATE $table
            SET 
                sent = ?,
                sent_at = ?
            WHERE hash_key = ?
        """.trimIndent()

        update(sql) { stmt ->
            stmt.setBoolean(1, true)
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
            stmt.setInt(3, hash(oppdrag))
        }
    }
}
