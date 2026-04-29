package speiderhytta.dora

import libs.jdbc.Dao
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * One row per `(app, sha, env, run_id)`. A re-run of the same commit produces
 * a second row with its own `outcome` and `runId` — that's how we count
 * deploy retries as separate attempts in CFR.
 *
 * `outcome` is one of `success`, `failure`, `cancelled`. Only successful rows
 * contribute to deploy frequency and lead-time percentiles; failures count
 * towards CFR; cancelled rows are excluded from the CFR denominator entirely.
 */
data class Deployment(
    val id: Long? = null,
    val app: String,
    val sha: String,
    val env: String,
    val commitTs: Instant,
    val deployStartedTs: Instant,
    val deployFinishedTs: Instant,
    val leadTimeSeconds: Long,
    val outcome: String,
    val runId: Long,
    val runUrl: String? = null,
) {
    companion object : Dao<Deployment> {
        override val table = "deployment"

        override fun from(rs: ResultSet) = Deployment(
            id = rs.getLong("id"),
            app = rs.getString("app"),
            sha = rs.getString("sha"),
            env = rs.getString("env"),
            commitTs = rs.getTimestamp("commit_ts").toInstant(),
            deployStartedTs = rs.getTimestamp("deploy_started_ts").toInstant(),
            deployFinishedTs = rs.getTimestamp("deploy_finished_ts").toInstant(),
            leadTimeSeconds = rs.getLong("lead_time_seconds"),
            outcome = rs.getString("outcome"),
            runId = rs.getLong("run_id"),
            runUrl = rs.getString("run_url"),
        )

        /**
         * Most recent deployment row for a given sha — typically the latest
         * re-run. Used by [IncidentService] to link an incident to "the"
         * deployment of a commit; the most recent attempt is the right one
         * since an earlier failed attempt couldn't have caused production
         * impact.
         */
        suspend fun findBySha(app: String, sha: String, env: String): Deployment? {
            val sql = """
                SELECT * FROM $table
                WHERE app = ? AND sha = ? AND env = ?
                ORDER BY deploy_finished_ts DESC
                LIMIT 1
            """.trimIndent()
            return query(sql) { stmt ->
                stmt.setString(1, app)
                stmt.setString(2, sha)
                stmt.setString(3, env)
            }.firstOrNull()
        }

        /**
         * Successful deploys only — feeds deploy frequency and lead-time
         * percentiles. Also, the default for the public `/deployments` API
         * because users care about "what shipped".
         */
        suspend fun selectSuccessfulFor(app: String, env: String, since: Instant, limit: Int = 1000): List<Deployment> {
            val sql = """
                SELECT * FROM $table
                WHERE app = ? AND env = ? AND outcome = 'success' AND deploy_finished_ts >= ?
                ORDER BY deploy_finished_ts DESC
                LIMIT ?
            """.trimIndent()
            return query(sql) { stmt ->
                stmt.setString(1, app)
                stmt.setString(2, env)
                stmt.setTimestamp(3, Timestamp.from(since))
                stmt.setInt(4, limit)
            }
        }

        /**
         * All deploy attempts including failures and cancellations — the raw
         * input for CFR. Filter outcomes downstream as needed.
         */
        suspend fun selectAllFor(app: String, env: String, since: Instant, limit: Int = 1000): List<Deployment> {
            val sql = """
                SELECT * FROM $table
                WHERE app = ? AND env = ? AND deploy_finished_ts >= ?
                ORDER BY deploy_finished_ts DESC
                LIMIT ?
            """.trimIndent()
            return query(sql) { stmt ->
                stmt.setString(1, app)
                stmt.setString(2, env)
                stmt.setTimestamp(3, Timestamp.from(since))
                stmt.setInt(4, limit)
            }
        }

        /**
         * Most recent successful deployment for an app whose
         * `deploy_finished_ts` falls within `[windowStart, windowEnd]`. Used
         * by the heuristic incident-to-deploy linker — only successful
         * deploys can plausibly have caused a production incident.
         */
        suspend fun mostRecentBetween(
            app: String,
            env: String,
            windowStart: Instant,
            windowEnd: Instant,
        ): Deployment? {
            val sql = """
                SELECT * FROM $table
                WHERE app = ? AND env = ? AND outcome = 'success'
                  AND deploy_finished_ts >= ? AND deploy_finished_ts <= ?
                ORDER BY deploy_finished_ts DESC
                LIMIT 1
            """.trimIndent()
            return query(sql) { stmt ->
                stmt.setString(1, app)
                stmt.setString(2, env)
                stmt.setTimestamp(3, Timestamp.from(windowStart))
                stmt.setTimestamp(4, Timestamp.from(windowEnd))
            }.firstOrNull()
        }
    }

    suspend fun insert(): Int {
        val sql = """
            INSERT INTO $table (
                app, sha, env, commit_ts, deploy_started_ts, deploy_finished_ts,
                lead_time_seconds, outcome, run_id, run_url
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (app, sha, env, run_id) DO NOTHING
        """.trimIndent()
        return update(sql) { stmt ->
            stmt.setString(1, app)
            stmt.setString(2, sha)
            stmt.setString(3, env)
            stmt.setTimestamp(4, Timestamp.from(commitTs))
            stmt.setTimestamp(5, Timestamp.from(deployStartedTs))
            stmt.setTimestamp(6, Timestamp.from(deployFinishedTs))
            stmt.setLong(7, leadTimeSeconds)
            stmt.setString(8, outcome)
            stmt.setLong(9, runId)
            stmt.setString(10, runUrl)
        }
    }
}
