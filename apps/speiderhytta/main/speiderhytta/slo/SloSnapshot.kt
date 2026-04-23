package speiderhytta.slo

import libs.jdbc.Dao
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * Periodically captured SLO state. helved-peisen reads this for trend charts;
 * the live "right now" view is computed directly from Prometheus instead.
 */
data class SloSnapshot(
    val id: Long? = null,
    val app: String,
    val sloName: String,
    val objective: Double,
    val sliValue: Double,
    val errorBudgetRemaining: Double,
    val burnRate1h: Double? = null,
    val burnRate6h: Double? = null,
    val capturedAt: Instant = Instant.now(),
) {
    companion object : Dao<SloSnapshot> {
        override val table = "slo_snapshot"

        override fun from(rs: ResultSet) = SloSnapshot(
            id = rs.getLong("id"),
            app = rs.getString("app"),
            sloName = rs.getString("slo_name"),
            objective = rs.getDouble("objective"),
            sliValue = rs.getDouble("sli_value"),
            errorBudgetRemaining = rs.getDouble("error_budget_remaining"),
            burnRate1h = rs.getDouble("burn_rate_1h").takeUnless { rs.wasNull() },
            burnRate6h = rs.getDouble("burn_rate_6h").takeUnless { rs.wasNull() },
            capturedAt = rs.getTimestamp("captured_at").toInstant(),
        )

        suspend fun selectFor(app: String, since: Instant, limit: Int = 1000): List<SloSnapshot> {
            val sql = """
                SELECT * FROM $table
                WHERE app = ? AND captured_at >= ?
                ORDER BY captured_at DESC
                LIMIT ?
            """.trimIndent()
            return query(sql) { stmt ->
                stmt.setString(1, app)
                stmt.setTimestamp(2, Timestamp.from(since))
                stmt.setInt(3, limit)
            }
        }
    }

    suspend fun insert(): Int {
        val sql = """
            INSERT INTO $table (app, slo_name, objective, sli_value, error_budget_remaining,
                                burn_rate_1h, burn_rate_6h, captured_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return update(sql) { stmt ->
            stmt.setString(1, app)
            stmt.setString(2, sloName)
            stmt.setDouble(3, objective)
            stmt.setDouble(4, sliValue)
            stmt.setDouble(5, errorBudgetRemaining)
            if (burnRate1h != null) stmt.setDouble(6, burnRate1h) else stmt.setNull(6, java.sql.Types.DOUBLE)
            if (burnRate6h != null) stmt.setDouble(7, burnRate6h) else stmt.setNull(7, java.sql.Types.DOUBLE)
            stmt.setTimestamp(8, Timestamp.from(capturedAt))
        }
    }
}
