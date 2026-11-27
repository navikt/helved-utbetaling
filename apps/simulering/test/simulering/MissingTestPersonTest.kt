package simulering

import models.Fagsystem
import models.Info
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MissingTestPersonTest {
    private val soap = Info.UgyldigRequest(Fagsystem.AAP, "Personen finnes ikke i PDL")
    private val rest = ResponsSimulering(
        statusKode = 4,
        statusMelding = "Navn er ikke funnet ved oppslag",
        beregning = RestBeregning("", "", BigDecimal.ZERO)
    )

    @Test
    fun `kjent manglende person mapper til samme resultat som SOAP`() {
        listOf(Fagsystem.AAP, Fagsystem.TILLEGGSSTØNADER).forEach { fagsystem ->
            val expected = Info.UgyldigRequest(fagsystem, "Personen finnes ikke i PDL")
            assertEquals(expected, rest.toSimulering(fagsystem))
            assertEquals(expected, rest.copy(beregning = null).toSimulering(fagsystem))
        }
    }

    @Test
    fun `andre feil og advarsler normaliseres ikke`() {
        listOf(0, 8, 12).forEach { status ->
            assertNotEquals(soap, rest.copy(statusKode = status).toSimulering(Fagsystem.AAP))
        }
        assertNotEquals(soap, rest.copy(statusMelding = "Annen advarsel").toSimulering(Fagsystem.AAP))
        assertNotEquals(soap, rest.copy(statusMelding = "Navn er ikke funnet ved oppslag ekstra").toSimulering(Fagsystem.AAP))
    }

    @Test
    fun `beregning med innhold skjules ikke som manglende person`() {
        val calculation = requireNotNull(rest.beregning)
        val period = PeriodeStoppnivaa(RestBeregningsperiode("2026-01-01", "2026-01-31"), RestStoppnivaa("AAP", "sak", "12345678910"))
        listOf(
            calculation.copy(gjelderId = "12345678910"),
            calculation.copy(datoBeregnet = "2026-01-20"),
            calculation.copy(belopTotalt = BigDecimal.ONE),
            calculation.copy(periodeStoppnivaa = listOf(period))
        ).forEach { populated ->
            val response = rest.copy(beregning = populated)
            assertEquals(response.copy(statusMelding = null).toSimulering(Fagsystem.AAP), response.toSimulering(Fagsystem.AAP))
        }
    }
}
