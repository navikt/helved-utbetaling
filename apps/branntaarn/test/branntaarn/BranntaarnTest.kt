package branntaarn

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranntaarnTest {
    @Test
    fun `ikke varseltid på helligdag`() {
        assertFalse(LocalDateTime.of(2026, 5, 17, 12, 0).erVarseltid())
    }

    @Test
    fun `ikke varseltid før kl 6`() {
        assertFalse(LocalDateTime.of(2026, 4, 22, 5, 59).erVarseltid())
    }

    @Test
    fun `ikke varseltid etter kl 21`() {
        assertFalse(LocalDateTime.of(2026, 4, 22, 22, 0).erVarseltid())
    }

    @Test
    fun `varseltid i arbeidstid`() {
        assertTrue(LocalDateTime.of(2026, 4, 22, 12, 0).erVarseltid())
    }
}
