package peisschtappern

import kotlin.test.Test
import kotlin.test.assertEquals

class DaoTest {
    @Test
    fun `header value preserves colons`() {
        assertEquals(
            Header.fromString("endret-aarsak:denne endring har vi gjort pga https://github.com/navikt/helved-utbetaling/issues/123"),
            Header("endret-aarsak", "denne endring har vi gjort pga https://github.com/navikt/helved-utbetaling/issues/123"),
        )
    }
}
