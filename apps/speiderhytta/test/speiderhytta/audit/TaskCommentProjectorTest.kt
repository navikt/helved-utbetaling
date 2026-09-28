package speiderhytta.audit

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import speiderhytta.TestRuntime
import speiderhytta.github.GithubIssueComment
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskCommentProjectorTest {
    @AfterTest fun reset() = TestRuntime.reset()

    @Test
    fun `oppretter en kommentar og oppdaterer samme kommentar ved ny commit`() = runTest(TestRuntime.context) {
        val client = TaskCommentClientFake()
        val projector = TaskCommentProjector(client, TestRuntime.context)
        val task = TaskReference("navikt/team-helved", 633)
        insertCommit("aaa1111", task, Instant.parse("2026-09-01T10:00:00Z"))

        projector.update(setOf(task))

        assertEquals(1, client.created.size)
        assertEquals(0, client.updated.size)
        assertTrue(client.created.single().body.orEmpty().contains("aaa1111"))

        insertCommit("bbb2222", task, Instant.parse("2026-09-02T10:00:00Z"))
        projector.update(setOf(task))

        assertEquals(1, client.created.size)
        assertEquals(1, client.updated.size)
        assertTrue(client.updated.single().body.orEmpty().contains("aaa1111"))
        assertTrue(client.updated.single().body.orEmpty().contains("bbb2222"))
        val state = withContext(TestRuntime.context) { libs.jdbc.concurrency.transaction { AuditTaskComment.lock(task) } }
        assertEquals(client.created.single().id, state.githubCommentId)
    }

    @Test
    fun `finner eksisterende kommentar med markør når id mangler`() = runTest(TestRuntime.context) {
        val task = TaskReference("navikt/team-helved", 633)
        val existing = GithubIssueComment(42, "${TaskCommentProjector.MARKER}\ngammel")
        val client = TaskCommentClientFake(comments = mutableListOf(existing))
        val projector = TaskCommentProjector(client, TestRuntime.context)
        insertCommit("aaa1111", task, Instant.parse("2026-09-01T10:00:00Z"))

        projector.update(setOf(task))

        assertEquals(0, client.created.size)
        assertEquals(listOf(42L), client.updated.map { it.id })
        val state = withContext(TestRuntime.context) { libs.jdbc.concurrency.transaction { AuditTaskComment.lock(task) } }
        assertEquals(42, state.githubCommentId)
    }

    @Test
    fun `oppretter ny kommentar hvis lagret kommentar er slettet`() = runTest(TestRuntime.context) {
        val task = TaskReference("navikt/team-helved", 633)
        val client = TaskCommentClientFake(deletedIds = mutableSetOf(99))
        val projector = TaskCommentProjector(client, TestRuntime.context)
        insertCommit("aaa1111", task, Instant.parse("2026-09-01T10:00:00Z"))
        withContext(TestRuntime.context) {
            libs.jdbc.concurrency.transaction { AuditTaskComment(task.repository, task.number, 99, "gammel").save() }
        }

        projector.update(setOf(task))

        assertEquals(1, client.created.size)
        assertEquals(0, client.updated.size)
        val state = withContext(TestRuntime.context) { libs.jdbc.concurrency.transaction { AuditTaskComment.lock(task) } }
        assertNotNull(state.githubCommentId)
        assertTrue(state.githubCommentId != 99L)
    }

    @Test
    fun `oppdaterer første Speiderhytta-kommentar og lar andre kommentarer stå`() = runTest(TestRuntime.context) {
        val task = TaskReference("navikt/team-helved", 633)
        val human = GithubIssueComment(1, "Menneskelig kommentar")
        val oldCorrelator = GithubIssueComment(2, "<!-- helved-issue-correlator:v1 -->\ngammel korrelator")
        val managedOne = GithubIssueComment(3, "${TaskCommentProjector.MARKER}\nførste")
        val managedTwo = GithubIssueComment(4, "${TaskCommentProjector.MARKER}\nandre")
        val client = TaskCommentClientFake(comments = mutableListOf(human, oldCorrelator, managedOne, managedTwo))
        val projector = TaskCommentProjector(client, TestRuntime.context)
        insertCommit("aaa1111", task, Instant.parse("2026-09-01T10:00:00Z"))

        projector.update(setOf(task))

        assertEquals(listOf(3L), client.updated.map { it.id })
        assertTrue(client.comments.any { it.id == human.id })
        assertTrue(client.comments.any { it.id == oldCorrelator.id })
        assertTrue(client.comments.any { it.id == managedTwo.id })
    }

    @Test
    fun `dekoder reell kommentarrespons og lar vanlig kommentar stå`() = runTest(TestRuntime.context) {
        val existing = libs.kotlinx.KotlinxJson
            .decodeFromString<List<GithubIssueComment>>(GithubPayloads.commentsResponse)
            .single()
        assertEquals(5_677_480_690L, existing.id)
        assertEquals("developer-three", existing.user?.login)
        assertEquals("Vanlig kommentar fra et teammedlem.", existing.body)

        val task = TaskReference("navikt/team-helved", 633)
        val client = TaskCommentClientFake(comments = mutableListOf(existing))
        val projector = TaskCommentProjector(client, TestRuntime.context)
        insertCommit("aaa1111", task, Instant.parse("2026-09-15T09:00:00Z"))

        projector.update(setOf(task))

        assertEquals(1, client.created.size)
        assertTrue(client.comments.any { it.id == existing.id && it.body == existing.body })
        assertEquals(1, client.comments.count { it.body?.startsWith(TaskCommentProjector.MARKER) == true })
    }

    private suspend fun insertCommit(sha: String, task: TaskReference, committedAt: Instant) {
        withContext(TestRuntime.context) {
            libs.jdbc.concurrency.transaction {
                AuditCommit(
                    repository = "navikt/helved-utbetaling",
                    sha = sha,
                    taskRepository = task.repository,
                    taskNumber = task.number,
                    message = "Endring $sha #${task.number}",
                    authorLogin = "developer-one",
                    authoredAt = committedAt,
                    committedAt = committedAt,
                    parents = kotlinx.serialization.json.JsonArray(emptyList()),
                    rawMetadata = kotlinx.serialization.json.JsonObject(emptyMap()),
                ).insert()
            }
        }
    }
}

class TaskCommentClientFake(
    val comments: MutableList<GithubIssueComment> = mutableListOf(),
    val deletedIds: MutableSet<Long> = mutableSetOf(),
) : TaskCommentClient {
    val created = mutableListOf<GithubIssueComment>()
    val updated = mutableListOf<GithubIssueComment>()
    private var nextId = 1000L

    override suspend fun comments(repository: String, taskNumber: Long) = comments.toList()

    override suspend fun create(repository: String, taskNumber: Long, body: String): GithubIssueComment =
        GithubIssueComment(nextId++, body).also { created.add(it); comments.add(it) }

    override suspend fun update(repository: String, commentId: Long, body: String): GithubIssueComment? {
        if (commentId in deletedIds) return null
        val result = GithubIssueComment(commentId, body)
        updated.add(result)
        comments.removeAll { it.id == commentId }
        comments.add(result)
        return result
    }

}
