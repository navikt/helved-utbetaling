@file:UseSerializers(libs.kotlinx.InstantSerializer::class)

package speiderhytta.audit

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import libs.jdbc.Dao
import speiderhytta.github.FetchStatus
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Serializable
data class AuditCommit(
    val id: Long? = null,
    val repository: String,
    val sha: String,
    val taskRepository: String? = null,
    val taskNumber: Long? = null,
    val message: String,
    val authorLogin: String? = null,
    val committerLogin: String? = null,
    val authoredAt: Instant,
    val committedAt: Instant,
    val signatureVerified: Boolean? = null,
    val verificationReason: String? = null,
    val parents: JsonElement,
    val rawMetadata: JsonElement,
    val capturedAt: Instant = Instant.now(),
) {
    companion object : Dao<AuditCommit> {
        override val table = "audit_commit"

        override fun from(rs: ResultSet) = AuditCommit(
            id = rs.getLong("id"),
            repository = rs.getString("repository"),
            sha = rs.getString("sha"),
            taskRepository = rs.getString("task_repository"),
            taskNumber = rs.nullableLong("task_number"),
            message = rs.getString("message"),
            authorLogin = rs.getString("author_login"),
            committerLogin = rs.getString("committer_login"),
            authoredAt = rs.getTimestamp("authored_at").toInstant(),
            committedAt = rs.getTimestamp("committed_at").toInstant(),
            signatureVerified = rs.nullableBoolean("signature_verified"),
            verificationReason = rs.getString("verification_reason"),
            parents = Json.parseToJsonElement(rs.getString("parents")),
            rawMetadata = Json.parseToJsonElement(rs.getString("raw_metadata")),
            capturedAt = rs.getTimestamp("captured_at").toInstant(),
        )

        suspend fun find(repository: String, sha: String): AuditCommit? = query(
            "SELECT * FROM $table WHERE repository = ? AND sha = ?",
        ) { stmt ->
            stmt.setString(1, repository)
            stmt.setString(2, sha)
        }.firstOrNull()

        suspend fun selectAllForTask(taskRepository: String, taskNumber: Long): List<AuditCommit> = query(
            """
            SELECT * FROM $table WHERE task_repository = ? AND task_number = ?
            ORDER BY committed_at DESC
            """.trimIndent(),
        ) { stmt ->
            stmt.setString(1, taskRepository)
            stmt.setLong(2, taskNumber)
        }

        suspend fun between(repository: String, after: Instant?, through: Instant): List<AuditCommit> {
            val afterClause = if (after == null) "" else "AND committed_at > ?"
            return query(
                """
                SELECT * FROM $table
                WHERE repository = ? $afterClause AND committed_at <= ?
                ORDER BY committed_at
                """.trimIndent(),
            ) { stmt ->
                stmt.setString(1, repository)
                if (after == null) stmt.setTimestamp(2, Timestamp.from(through)) else {
                    stmt.setTimestamp(2, Timestamp.from(after))
                    stmt.setTimestamp(3, Timestamp.from(through))
                }
            }
        }
    }

    suspend fun insert(): Int = update(
        """
        INSERT INTO $table (
            repository, sha, task_repository, task_number, message, author_login, committer_login,
            authored_at, committed_at, signature_verified, verification_reason, parents, raw_metadata, captured_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
        ON CONFLICT (repository, sha) DO NOTHING
        """.trimIndent(),
    ) { stmt ->
        stmt.setString(1, repository)
        stmt.setString(2, sha)
        stmt.setString(3, taskRepository)
        taskNumber?.let { stmt.setLong(4, it) } ?: stmt.setNull(4, java.sql.Types.BIGINT)
        stmt.setString(5, message)
        stmt.setString(6, authorLogin)
        stmt.setString(7, committerLogin)
        stmt.setTimestamp(8, Timestamp.from(authoredAt))
        stmt.setTimestamp(9, Timestamp.from(committedAt))
        signatureVerified?.let { stmt.setBoolean(10, it) } ?: stmt.setNull(10, java.sql.Types.BOOLEAN)
        stmt.setString(11, verificationReason)
        stmt.setString(12, parents.toString())
        stmt.setString(13, rawMetadata.toString())
        stmt.setTimestamp(14, Timestamp.from(capturedAt))
    }
}

@Serializable
data class AuditWorkflowExecution(
    val id: Long? = null,
    val repository: String,
    val app: String,
    val workflowFile: String,
    val workflowPath: String? = null,
    val runId: Long,
    val runAttempt: Int,
    val headSha: String,
    val event: String,
    val status: String,
    val conclusion: String? = null,
    val actorLogin: String? = null,
    val triggeringActorLogin: String? = null,
    val createdAt: Instant,
    val runStartedAt: Instant? = null,
    val updatedAt: Instant,
    val runUrl: String,
    val rawMetadata: JsonElement,
    val capturedAt: Instant = Instant.now(),
) {
    companion object : Dao<AuditWorkflowExecution> {
        override val table = "audit_workflow_execution"

        override fun from(rs: ResultSet) = AuditWorkflowExecution(
            id = rs.getLong("id"), repository = rs.getString("repository"), app = rs.getString("app"),
            workflowFile = rs.getString("workflow_file"), workflowPath = rs.getString("workflow_path"),
            runId = rs.getLong("run_id"), runAttempt = rs.getInt("run_attempt"), headSha = rs.getString("head_sha"),
            event = rs.getString("event"), status = rs.getString("status"), conclusion = rs.getString("conclusion"),
            actorLogin = rs.getString("actor_login"), triggeringActorLogin = rs.getString("triggering_actor_login"),
            createdAt = rs.getTimestamp("created_at").toInstant(), runStartedAt = rs.getTimestamp("run_started_at")?.toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(), runUrl = rs.getString("run_url"),
            rawMetadata = Json.parseToJsonElement(rs.getString("raw_metadata")),
            capturedAt = rs.getTimestamp("captured_at").toInstant(),
        )

        suspend fun find(repository: String, runId: Long, attempt: Int? = null): AuditWorkflowExecution? {
            val attemptClause = if (attempt == null) "" else "AND run_attempt = ?"
            return query(
                "SELECT * FROM $table WHERE repository = ? AND run_id = ? $attemptClause ORDER BY run_attempt DESC LIMIT 1",
            ) { stmt ->
                stmt.setString(1, repository)
                stmt.setLong(2, runId)
                attempt?.let { stmt.setInt(3, it) }
            }.firstOrNull()
        }

        suspend fun attempts(repository: String, runId: Long): List<AuditWorkflowExecution> = query(
            "SELECT * FROM $table WHERE repository = ? AND run_id = ? ORDER BY run_attempt",
        ) { stmt -> stmt.setString(1, repository); stmt.setLong(2, runId) }

        suspend fun previousSuccessfulDeploymentHead(repository: String, app: String, before: Instant): String? = query(
            """
            SELECT r.* FROM $table r
            WHERE r.repository = ? AND r.app = ? AND r.updated_at < ?
              AND EXISTS (
                  SELECT 1 FROM audit_workflow_job j
                  WHERE j.workflow_execution_id = r.id AND j.name = 'deploy-prod' AND j.conclusion = 'success'
              )
            ORDER BY r.updated_at DESC LIMIT 1
            """.trimIndent(),
        ) { stmt ->
            stmt.setString(1, repository)
            stmt.setString(2, app)
            stmt.setTimestamp(3, Timestamp.from(before))
        }.firstOrNull()?.headSha
    }

    suspend fun insert(): Int = update(
        """
        INSERT INTO $table (
            repository, app, workflow_file, workflow_path, run_id, run_attempt, head_sha, event, status,
            conclusion, actor_login, triggering_actor_login, created_at, run_started_at, updated_at,
            run_url, raw_metadata, captured_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (repository, run_id, run_attempt) DO NOTHING
        """.trimIndent(),
    ) { stmt ->
        stmt.setString(1, repository); stmt.setString(2, app); stmt.setString(3, workflowFile)
        stmt.setString(4, workflowPath); stmt.setLong(5, runId); stmt.setInt(6, runAttempt); stmt.setString(7, headSha)
        stmt.setString(8, event); stmt.setString(9, status); stmt.setString(10, conclusion)
        stmt.setString(11, actorLogin); stmt.setString(12, triggeringActorLogin)
        stmt.setTimestamp(13, Timestamp.from(createdAt)); stmt.setTimestamp(14, runStartedAt?.let(Timestamp::from))
        stmt.setTimestamp(15, Timestamp.from(updatedAt)); stmt.setString(16, runUrl)
        stmt.setString(17, rawMetadata.toString()); stmt.setTimestamp(18, Timestamp.from(capturedAt))
    }
}

@Serializable
data class AuditWorkflowSource(
    val workflowExecutionId: Long,
    val path: String,
    val sourceRef: String,
    val blobSha: String,
    val content: String,
    val contentSha256: String,
    val rawMetadata: JsonElement,
) {
    companion object : Dao<AuditWorkflowSource> {
        override val table = "audit_workflow_source"
        override fun from(rs: ResultSet) = AuditWorkflowSource(
            rs.getLong("workflow_execution_id"), rs.getString("path"), rs.getString("source_ref"),
            rs.getString("blob_sha"), rs.getString("content"), rs.getString("content_sha256"),
            Json.parseToJsonElement(rs.getString("raw_metadata")),
        )

        suspend fun find(workflowExecutionId: Long) = query(
            "SELECT * FROM $table WHERE workflow_execution_id = ?",
        ) { stmt -> stmt.setLong(1, workflowExecutionId) }.firstOrNull()
    }

    suspend fun insert() = update(
        """
        INSERT INTO $table (workflow_execution_id, path, source_ref, blob_sha, content, content_sha256, raw_metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT (workflow_execution_id) DO NOTHING
        """.trimIndent(),
    ) { stmt ->
        stmt.setLong(1, workflowExecutionId); stmt.setString(2, path); stmt.setString(3, sourceRef)
        stmt.setString(4, blobSha); stmt.setString(5, content); stmt.setString(6, contentSha256)
        stmt.setString(7, rawMetadata.toString())
    }
}

@Serializable
data class AuditWorkflowJob(
    val id: Long? = null,
    val workflowExecutionId: Long,
    val githubJobId: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val rawMetadata: JsonElement,
) {
    companion object : Dao<AuditWorkflowJob> {
        override val table = "audit_workflow_job"
        override fun from(rs: ResultSet) = AuditWorkflowJob(
            rs.getLong("id"), rs.getLong("workflow_execution_id"), rs.getLong("github_job_id"), rs.getString("name"),
            rs.getString("status"), rs.getString("conclusion"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("started_at")?.toInstant(), rs.getTimestamp("completed_at")?.toInstant(),
            Json.parseToJsonElement(rs.getString("raw_metadata")),
        )
        suspend fun find(workflowExecutionId: Long, githubJobId: Long) = query(
            "SELECT * FROM $table WHERE workflow_execution_id = ? AND github_job_id = ?",
        ) { stmt -> stmt.setLong(1, workflowExecutionId); stmt.setLong(2, githubJobId) }.firstOrNull()

        suspend fun forExecution(workflowExecutionId: Long) = query(
            "SELECT * FROM $table WHERE workflow_execution_id = ? ORDER BY created_at, github_job_id",
        ) { stmt -> stmt.setLong(1, workflowExecutionId) }
    }

    suspend fun insert() = update(
        """
        INSERT INTO $table (workflow_execution_id, github_job_id, name, status, conclusion, created_at, started_at, completed_at, raw_metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT (workflow_execution_id, github_job_id) DO NOTHING
        """.trimIndent(),
    ) { stmt ->
        stmt.setLong(1, workflowExecutionId); stmt.setLong(2, githubJobId); stmt.setString(3, name); stmt.setString(4, status)
        stmt.setString(5, conclusion); stmt.setTimestamp(6, Timestamp.from(createdAt)); stmt.setTimestamp(7, startedAt?.let(Timestamp::from))
        stmt.setTimestamp(8, completedAt?.let(Timestamp::from)); stmt.setString(9, rawMetadata.toString())
    }
}

@Serializable
data class AuditWorkflowStep(
    val workflowJobId: Long,
    val number: Int,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val rawMetadata: JsonElement,
) {
    companion object : Dao<AuditWorkflowStep> {
        override val table = "audit_workflow_step"
        override fun from(rs: ResultSet) = AuditWorkflowStep(
            rs.getLong("workflow_job_id"), rs.getInt("step_number"), rs.getString("name"), rs.getString("status"),
            rs.getString("conclusion"), rs.getTimestamp("started_at")?.toInstant(), rs.getTimestamp("completed_at")?.toInstant(),
            Json.parseToJsonElement(rs.getString("raw_metadata")),
        )

        suspend fun forJob(workflowJobId: Long) = query(
            "SELECT * FROM $table WHERE workflow_job_id = ? ORDER BY step_number",
        ) { stmt -> stmt.setLong(1, workflowJobId) }
    }

    suspend fun insert() = update(
        """
        INSERT INTO $table (workflow_job_id, step_number, name, status, conclusion, started_at, completed_at, raw_metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT (workflow_job_id, step_number) DO NOTHING
        """.trimIndent(),
    ) { stmt ->
        stmt.setLong(1, workflowJobId); stmt.setInt(2, number); stmt.setString(3, name); stmt.setString(4, status)
        stmt.setString(5, conclusion); stmt.setTimestamp(6, startedAt?.let(Timestamp::from))
        stmt.setTimestamp(7, completedAt?.let(Timestamp::from)); stmt.setString(8, rawMetadata.toString())
    }
}

data class AuditWorkflowExecutionCommit(val workflowExecutionId: Long, val commitId: Long) {
    companion object : Dao<AuditWorkflowExecutionCommit> {
        override val table = "audit_workflow_execution_commit"
        override fun from(rs: ResultSet) = AuditWorkflowExecutionCommit(rs.getLong("workflow_execution_id"), rs.getLong("commit_id"))

        suspend fun forExecution(workflowExecutionId: Long) = query(
            "SELECT * FROM $table WHERE workflow_execution_id = ?",
        ) { stmt -> stmt.setLong(1, workflowExecutionId) }

        suspend fun commitsForExecution(workflowExecutionId: Long): List<AuditCommit> = AuditCommit.query(
            """
            SELECT c.* FROM audit_commit c
            JOIN $table ac ON ac.commit_id = c.id
            WHERE ac.workflow_execution_id = ? ORDER BY c.committed_at
            """.trimIndent(),
        ) { stmt -> stmt.setLong(1, workflowExecutionId) }
    }

    suspend fun insert() = update(
        "INSERT INTO $table (workflow_execution_id, commit_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
    ) { stmt -> stmt.setLong(1, workflowExecutionId); stmt.setLong(2, commitId) }
}

@Serializable
data class AuditControlSnapshot(
    val id: Long? = null,
    val repository: String,
    val branch: String,
    val controlType: String,
    val fetchStatus: FetchStatus,
    val payload: JsonElement,
    val payloadSha256: String,
    val capturedAt: Instant = Instant.now(),
) {
    companion object : Dao<AuditControlSnapshot> {
        override val table = "audit_control_snapshot"
        override fun from(rs: ResultSet) = AuditControlSnapshot(
            rs.getLong("id"), rs.getString("repository"), rs.getString("branch"), rs.getString("control_type"),
            FetchStatus.valueOf(rs.getString("fetch_status")), Json.parseToJsonElement(rs.getString("payload")),
            rs.getString("payload_sha256"), rs.getTimestamp("captured_at").toInstant(),
        )

        suspend fun latest(repository: String, branch: String, type: String) = query(
            "SELECT * FROM $table WHERE repository = ? AND branch = ? AND control_type = ? ORDER BY captured_at DESC, id DESC LIMIT 1",
        ) { stmt -> stmt.setString(1, repository); stmt.setString(2, branch); stmt.setString(3, type) }.firstOrNull()

        suspend fun at(repository: String, branch: String, at: Instant): List<AuditControlSnapshot> = query(
            """
            SELECT DISTINCT ON (control_type) * FROM $table
            WHERE repository = ? AND branch = ? AND captured_at <= ?
            ORDER BY control_type, captured_at DESC, id DESC
            """.trimIndent(),
        ) { stmt ->
            stmt.setString(1, repository); stmt.setString(2, branch); stmt.setTimestamp(3, Timestamp.from(at))
        }
    }

    suspend fun insert() = update(
        """
        INSERT INTO $table (
            repository, branch, control_type, fetch_status, payload, payload_sha256, captured_at
        ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
        """.trimIndent(),
    ) { stmt ->
        stmt.setString(1, repository); stmt.setString(2, branch); stmt.setString(3, controlType); stmt.setString(4, fetchStatus.name)
        stmt.setString(5, payload.toString()); stmt.setString(6, payloadSha256); stmt.setTimestamp(7, Timestamp.from(capturedAt))
    }
}

private fun ResultSet.nullableLong(column: String) = getLong(column).takeUnless { wasNull() }
private fun ResultSet.nullableBoolean(column: String) = getBoolean(column).takeUnless { wasNull() }
