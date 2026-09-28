package speiderhytta.audit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import libs.jdbc.Dao
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import speiderhytta.github.GithubClient
import speiderhytta.github.GithubIssueComment
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

interface TaskCommentClient {
    suspend fun comments(repository: String, taskNumber: Long): List<GithubIssueComment>
    suspend fun create(repository: String, taskNumber: Long, body: String): GithubIssueComment
    suspend fun update(repository: String, commentId: Long, body: String): GithubIssueComment?
}

fun GithubClient.asTaskCommentClient(): TaskCommentClient = object : TaskCommentClient {
    override suspend fun comments(repository: String, taskNumber: Long) = issueComments(repository, taskNumber)
    override suspend fun create(repository: String, taskNumber: Long, body: String) =
        createIssueComment(repository, taskNumber, body)
    override suspend fun update(repository: String, commentId: Long, body: String) =
        updateIssueComment(repository, commentId, body)
}

class TaskCommentProjector(
    private val client: TaskCommentClient,
    private val jdbcCtx: CoroutineDatasource,
) {
    private val locks = ConcurrentHashMap<TaskReference, Mutex>()

    suspend fun update(tasks: Set<TaskReference>) {
        tasks.forEach { task -> locks.computeIfAbsent(task) { Mutex() }.withLock { update(task) } }
    }

    private suspend fun update(task: TaskReference) {
        val projection = withContext(jdbcCtx) {
            transaction {
                val state = AuditTaskComment.lock(task)
                val commits = AuditCommit.selectAllForTask(task.repository, task.number)
                if (commits.isEmpty()) return@transaction null
                val body = renderTaskComment(task, commits)
                val bodyHash = sha256(body)
                if (state.renderedSha256 == bodyHash && state.githubCommentId != null) return@transaction null
                TaskCommentProjection(state.githubCommentId, body, bodyHash)
            }
        } ?: return

        val updated = projection.knownCommentId?.let { client.update(task.repository, it, projection.body) }
        val comment = updated ?: run {
            val managed = client.comments(task.repository, task.number).firstOrNull { it.body?.startsWith(MARKER) == true }
            managed?.let { found ->
                client.update(task.repository, found.id, projection.body)
            } ?: client.create(task.repository, task.number, projection.body)
        }

        withContext(jdbcCtx) {
            transaction {
                AuditTaskComment(task.repository, task.number, comment.id, projection.bodyHash).save()
            }
        }
    }

    companion object {
        const val MARKER = "<!-- speiderhytta-task-commits:v1 -->"
    }
}

private data class TaskCommentProjection(
    val knownCommentId: Long?,
    val body: String,
    val bodyHash: String,
)

data class AuditTaskComment(
    val taskRepository: String,
    val taskNumber: Long,
    val githubCommentId: Long? = null,
    val renderedSha256: String? = null,
    val updatedAt: Instant = Instant.now(),
) {
    companion object : Dao<AuditTaskComment> {
        override val table = "audit_task_comment"

        override fun from(rs: ResultSet) = AuditTaskComment(
            taskRepository = rs.getString("task_repository"),
            taskNumber = rs.getLong("task_number"),
            githubCommentId = rs.getLong("github_comment_id").takeUnless { rs.wasNull() },
            renderedSha256 = rs.getString("rendered_sha256"),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )

        suspend fun lock(task: TaskReference): AuditTaskComment {
            update(
                """
                INSERT INTO $table (task_repository, task_number)
                VALUES (?, ?) ON CONFLICT DO NOTHING
                """.trimIndent(),
            ) { stmt -> stmt.setString(1, task.repository); stmt.setLong(2, task.number) }
            return query(
                "SELECT * FROM $table WHERE task_repository = ? AND task_number = ? FOR UPDATE",
            ) { stmt -> stmt.setString(1, task.repository); stmt.setLong(2, task.number) }.single()
        }
    }

    suspend fun save() = update(
        """
        INSERT INTO $table (task_repository, task_number, github_comment_id, rendered_sha256, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (task_repository, task_number) DO UPDATE SET
            github_comment_id = EXCLUDED.github_comment_id,
            rendered_sha256 = EXCLUDED.rendered_sha256,
            updated_at = EXCLUDED.updated_at
        """.trimIndent(),
    ) { stmt ->
        stmt.setString(1, taskRepository)
        stmt.setLong(2, taskNumber)
        githubCommentId?.let { stmt.setLong(3, it) } ?: stmt.setNull(3, java.sql.Types.BIGINT)
        stmt.setString(4, renderedSha256)
        stmt.setTimestamp(5, Timestamp.from(updatedAt))
    }
}

private fun renderTaskComment(task: TaskReference, commits: List<AuditCommit>): String = buildString {
    appendLine(TaskCommentProjector.MARKER)
    appendLine()
    appendLine("## Relaterte commits")
    appendLine()
    appendLine("| Repository | Commit | Melding | Forfatter | Dato |")
    appendLine("|---|---|---|---|---|")
    commits.forEach { commit ->
        val shortSha = commit.sha.take(7)
        val subject = commit.message.lineSequence().firstOrNull().orEmpty().markdownCell()
        val author = (commit.authorLogin ?: commit.committerLogin ?: "ukjent").markdownCell()
        val date = DATE.format(commit.committedAt)
        appendLine("| `${commit.repository.markdownCell()}` | [$shortSha](https://github.com/${commit.repository}/commit/${commit.sha}) | $subject | @$author | $date |")
    }
    appendLine()
    append("_Oppdatert automatisk av Speiderhytta for [${task.repository}#${task.number}](https://github.com/${task.repository}/issues/${task.number})._")
}

private fun String.markdownCell() = replace("|", "\\|").replace("\n", " ").replace("\r", " ")

private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
