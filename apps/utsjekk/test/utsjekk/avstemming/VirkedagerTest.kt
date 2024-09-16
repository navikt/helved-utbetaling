package utsjekk.avstemming

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VirkedagerTest {
    private val skjærTorsdag2021 = LocalDate.of(2021, 4, 1)
    private val skjærTorsdag2022 = LocalDate.of(2022, 4, 14)

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