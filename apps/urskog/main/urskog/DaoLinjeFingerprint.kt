package urskog

import libs.jdbc.Dao
import libs.utils.secureLog
import libs.utils.sha256
import no.trygdeetaten.skjema.oppdrag.Oppdrag110
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import java.sql.ResultSet
import java.time.LocalDateTime

data class DaoLinjeFingerprint(
    val fingerprint: String,
    val sakId: String,
    val oppdragHash: String,
    val delytelseId: String?,
    val cancelled: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object : Dao<DaoLinjeFingerprint> {
        override val table = "oppdragslinje_fingerprint"

        override fun from(rs: ResultSet) = DaoLinjeFingerprint(
            fingerprint = rs.getString("fingerprint"),
            sakId = rs.getString("sak_id"),
            oppdragHash = rs.getString("oppdrag_hash"),
            delytelseId = rs.getString("delytelse_id"),
            cancelled = rs.getBoolean("cancelled"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
        )

        fun fingerprint(oppdrag110: Oppdrag110, linje: OppdragsLinje150): String {
            val input = listOf(
                oppdrag110.fagsystemId,
                oppdrag110.oppdragGjelderId,
                oppdrag110.kodeFagomraade,
                linje.kodeKlassifik,
                linje.datoVedtakFom.toString(),
                linje.datoVedtakTom?.toString() ?: "",
                linje.sats.toPlainString(),
                linje.typeSats,
            ).joinToString("|")
            return input.sha256()
        }
    }

    /**
     * Insert fingerprint for an active payment line.
     * Returns true if this is a new fingerprint (no duplicate).
     * Returns true if fingerprint existed but was cancelled (reclaimed after OPPH).
     * Returns false if fingerprint already exists and is not cancelled (potential duplicate).
     */
    suspend fun insertOrReclaim(): Boolean {
        val sql = """
            INSERT INTO $table (fingerprint, sak_id, oppdrag_hash, delytelse_id, cancelled)
            VALUES (?, ?, ?, ?, false)
            ON CONFLICT (fingerprint) DO UPDATE
                SET cancelled = false,
                    oppdrag_hash = EXCLUDED.oppdrag_hash,
                    delytelse_id = EXCLUDED.delytelse_id
                WHERE $table.cancelled = true
        """.trimIndent()

        val rowsAffected = update(sql) { stmt ->
            stmt.setString(1, fingerprint)
            stmt.setString(2, sakId)
            stmt.setString(3, oppdragHash)
            stmt.setString(4, delytelseId)
        }

        return rowsAffected > 0
    }

    /**
     * Mark a fingerprint as cancelled (e.g. when an OPPH line is sent).
     * Allows the same period to be re-sent later without triggering duplicate detection.
     */
    suspend fun markCancelled(): Boolean {
        val sql = """
            UPDATE $table
            SET cancelled = true
            WHERE fingerprint = ?
        """.trimIndent()

        return update(sql) { stmt ->
            stmt.setString(1, fingerprint)
        } > 0
    }
}
