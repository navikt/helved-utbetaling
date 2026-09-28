package speiderhytta

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigTest {
    @Test
    fun `task-kommentarer er deaktivert som standard`() {
        assertFalse(AuditConfig().taskCommentsEnabled)
    }

    @Test
    fun `task-kommentarer kan aktiveres eksplisitt`() {
        assertTrue(AuditConfig(taskCommentsEnabled = true).taskCommentsEnabled)
    }
}
