package speiderhytta.dora

import libs.jdbc.Dao
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

data class Incident(
    val id: Long? = null,
    val githubIssue: Long,
    val app: String,
    val title: String,
    val openedAt: Instant,
    val resolvedAt: Instant? = null,
    val mttrSeconds: Long? = null,
    val causedBySha: String? = null,
    val causedByDeploymentId: Long? = null,
) {
    companion object : Dao<Incident> {
        override val table = "incident"

        override fun from(rs: ResultSet) = Incident(
            id = rs.getLong("id"),
            githubIssue = rs.getLong("github_issue"),
            app = rs.getString("app"),
            title = rs.getString("title"),
            openedAt = rs.getTimestamp("opened_at").toInstant(),
            resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
            mttrSeconds = rs.getLong("mttr_seconds").takeUnless { rs.wasNull() },
            causedBySha = rs.getString("caused_by_sha"),
            causedByDeploymentId = rs.getLong("caused_by_deployment_id").takeUnless { rs.wasNull() },
        )

        suspend fun findByIssue(issue: Long): Incident? {
            val sql = "SELECT * FROM $table WHERE github_issue = ? LIMIT 1"
            return query(sql) { stmt -> stmt.setLong(1, issue) }.firstOrNull()
        }

        suspend fun selectFor(app: String, since: Instant, limit: Int = 1000): List<Incident> {
            val sql = """
                SELECT * FROM $table
                WHERE app = ? AND opened_at >= ?
                ORDER BY opened_at DESC
                LIMIT ?
            """.trimIndent()
            return query(sql) { stmt ->
                stmt.setString(1, app)
                stmt.setTimestamp(2, Timestamp.from(since))
                stmt.setInt(3, limit)
            }
        }

        suspend fun lastFor(app: String): Incident? = selectFor(app, Instant.EPOCH, 1).firstOrNull()
    }

    suspend fun upsert(): Int {
        val sql = """
            INSERT INTO $table (
                github_issue, app, title, opened_at, resolved_at, mttr_seconds,
                caused_by_sha, caused_by_deployment_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (github_issue) DO UPDATE SET
                title                   = EXCLUDED.title,
                resolved_at             = EXCLUDED.resolved_at,
                mttr_seconds            = EXCLUDED.mttr_seconds,
                caused_by_sha           = COALESCE(EXCLUDED.caused_by_sha, incident.caused_by_sha),
                caused_by_deployment_id = COALESCE(EXCLUDED.caused_by_deployment_id, incident.caused_by_deployment_id)
        """.trimIndent()
        return update(sql) { stmt ->
            stmt.setLong(1, githubIssue)
            stmt.setString(2, app)
            stmt.setString(3, title)
            stmt.setTimestamp(4, Timestamp.from(openedAt))
            stmt.setTimestamp(5, resolvedAt?.let(Timestamp::from))
            if (mttrSeconds != null) stmt.setLong(6, mttrSeconds) else stmt.setNull(6, java.sql.Types.BIGINT)
            stmt.setString(7, causedBySha)
            if (causedByDeploymentId != null) stmt.setLong(8, causedByDeploymentId) else stmt.setNull(8, java.sql.Types.BIGINT)
        }
    }
}
