package speiderhytta.audit

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskReferenceTest {
    @Test
    fun `leser task-nummer først i commit-meldingen`() {
        assertEquals(
            TaskReference("navikt/team-helved", 123),
            taskReference("#123 fjerner unødvendig logglinje", "navikt/team-helved"),
        )
    }

    @Test
    fun `bruker BAU når meldingen ikke har task-nummer med hash`() {
        val bau = TaskReference("navikt/team-helved", 511)
        assertEquals(bau, taskReference("Fjerner unødvendig logglinje", "navikt/team-helved"))
        assertEquals(bau, taskReference("https://github.com/navikt/team-helved/issues/456", "navikt/team-helved"))
        assertEquals(bau, taskReference("", "navikt/team-helved"))
        assertEquals(bau, taskReference("Merge pull request #342 from navikt/dependabot", "navikt/team-helved"))
    }

    @Test
    fun `leser task-nummer senere i commit-meldingen`() {
        assertEquals(
            TaskReference("navikt/team-helved", 123),
            taskReference("Fix #123", "navikt/team-helved"),
        )
    }

    @Test
    fun `tar første referanse og ignorerer resten`() {
        assertEquals(TaskReference("navikt/team-helved", 123), taskReference("Fix #123 og #456", "navikt/team-helved"))
        assertEquals(TaskReference("navikt/team-helved", 123), taskReference("Fjerner logging\n\nSe #123 og #456", "navikt/team-helved"))
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
