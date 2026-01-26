package utsjekk.utbetaling

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class UtbetalingDomainToApiTest {
    private val skjærTorsdag2021 = LocalDate.of(2021, 4, 1)
    private val skjærTorsdag2022 = LocalDate.of(2022, 4, 14)

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

        assertEquals(22, api.perioder.size)
    }

    @Test
    fun `Hent virkedag allmenlig måndag`() {
        val allmenligMåndag = LocalDate.of(2020, 10, 26)
        assertEquals(allmenligMåndag.nesteVirkedag(), allmenligMåndag.plusDays(1))
    }

    @Test
    fun `Hent virkedag allmenlig fredag`() {
        val allmenligFredag = LocalDate.of(2020, 10, 30)
        assertEquals(allmenligFredag.nesteVirkedag(), allmenligFredag.plusDays(3))
    }

    @Test
    fun `Hent virkedag skjærtorsdag 2021`() {
        assertEquals(skjærTorsdag2021.nesteVirkedag(), skjærTorsdag2021.plusDays(5))
    }

    @Test
    fun `Hent virkedag skjærtorsdag 2022`() {
        assertEquals(skjærTorsdag2022.nesteVirkedag(), skjærTorsdag2022.plusDays(5))
    }
}
