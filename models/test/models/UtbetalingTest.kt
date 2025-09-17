package models

import org.junit.jupiter.api.assertThrows
import kotlin.test.*
import java.util.UUID

class UtbetalingTest {

    @Test
    fun `can decode PeriodeId`() {
        val expected = PeriodeId(UUID.fromString("850a4857-fa4e-4d0d-934a-240f7c76c8a4").toString())
        val actual = PeriodeId.decode("hQpIV/pOTQ2TSiQPfHbIpA==")
        assertEquals(expected, actual)
    }

    @Test
    fun `can decode old PeriodeId`() {
        val expected = PeriodeId("dette-er-en-gammel-id")
        val actual = PeriodeId.decode("dette-er-en-gammel-id")
        assertEquals(expected, actual)
    }

    @Test
    fun `will fail if PeriodeId is too long`() {
        assertThrows<IllegalArgumentException> {
            PeriodeId("123456789101234567891012345678910")
        }
    }
}
