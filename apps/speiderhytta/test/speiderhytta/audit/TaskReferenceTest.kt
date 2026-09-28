package speiderhytta.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskReferenceTest {
    @Test
    fun `leser entydig task-trailer`() {
        assertEquals(
            TaskReference("navikt/team-helved", 123),
            taskReference("Legg til audit\n\nTask: navikt/team-helved#123"),
        )
    }

    @Test
    fun `leser github issue-url`() {
        assertEquals(
            TaskReference("navikt/team-helved", 456),
            taskReference("Task: https://github.com/navikt/team-helved/issues/456"),
        )
    }

    @Test
    fun `leser entydig issue-nummer med standardrepo`() {
        assertEquals(
            TaskReference("navikt/team-helved", 123),
            taskReference("Fix #123", "navikt/team-helved"),
        )
    }

    @Test
    fun `ignorerer flere issue-numre uten entydig kobling`() {
        assertNull(taskReference("Fix #123 og #456", "navikt/team-helved"))
    }

    @Test
    fun `ignorerer pull request-nummer i merge-commit`() {
        assertEquals(
            TaskReference("navikt/team-helved", 511),
            taskReference(
                "Merge pull request #342 from navikt/dependabot\n\n#511: Bump dependency",
                "navikt/team-helved",
            ),
        )
    }
}
