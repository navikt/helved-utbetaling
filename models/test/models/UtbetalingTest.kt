package models

import kotlin.test.*
import java.util.UUID

class UtbetalingTest {

    @Test
    fun `can decode PeriodeId`() {
        val expected = PeriodeId(UUID.fromString("850a4857-fa4e-4d0d-934a-240f7c76c8a4"))
        val actual = PeriodeId.decode("hQpIV/pOTQ2TSiQPfHbIpA==")
        assertEquals(expected, actual)
    }
}
