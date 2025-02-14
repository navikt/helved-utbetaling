package utsjekk.utbetaling

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class UtbetalingDomainToApiTest {
    @Test
    fun `mapper fra feit periode til individuelle dager`() {
        val domain = Utbetaling(
            sakId = SakId("POUAAP-2502061253"),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            perioder = listOf(
                Utbetalingsperiode(
                    fom = LocalDate.parse("2024-12-02"),
                    tom = LocalDate.parse("2024-12-31"),
                    beløp = 1800u,
                    betalendeEnhet = null,
                    fastsattDagsats = 2000u,
                )
            ),
            satstype = Satstype.VIRKEDAG,
            beslutterId = Navident("B22222"),
            personident = Personident("01489115995"),
            behandlingId = BehandlingId("1"),
            lastPeriodeId = PeriodeId(UUID.fromString("848a1ca1-62bc-4a35-8070-73e69552d19e")),
            saksbehandlerId = Navident("S11111"),
            vedtakstidspunkt = LocalDateTime.parse("2025-02-06T12:40:00"),
            avvent = null,
        )
        val api = UtbetalingApi.from(domain)

        assertEquals(20, api.perioder.size)
    }
}
