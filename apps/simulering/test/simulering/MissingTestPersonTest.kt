package simulering

import models.Fagsystem
import models.Info
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissingTestPersonTest {
    private val soap = Info.UgyldigRequest(Fagsystem.AAP, "Personen finnes ikke i PDL")
    private val rest = ResponsSimulering(
        statusKode = 4,
        statusMelding = "Navn er ikke funnet ved oppslag",
        beregning = RestBeregning("", "", BigDecimal.ZERO)
    )

    @Test
    fun `kjent manglende testperson gir ikke mismatch alert i dev`() {
        assertTrue(isMissingTestPersonMismatch("dev-gcp", soap, rest))
        assertTrue(isMissingTestPersonMismatch("dev-gcp", soap, rest.copy(beregning = null)))
        assertFalse(isMissingTestPersonMismatch("prod-gcp", soap, rest))
        assertFalse(isMissingTestPersonMismatch("test-cluster", soap, rest))
    }

    @Test
    fun `andre feil og advarsler beholder mismatch alert`() {
        assertFalse(isMissingTestPersonMismatch("dev-gcp", Info.UgyldigRequest(Fagsystem.AAP, "Annen feil"), rest))
        assertFalse(isMissingTestPersonMismatch("dev-gcp", Info.Feilet(Fagsystem.AAP, "Personen finnes ikke i PDL"), rest))
        assertFalse(isMissingTestPersonMismatch("dev-gcp", soap, rest.copy(statusKode = 8)))
        assertFalse(isMissingTestPersonMismatch("dev-gcp", soap, rest.copy(statusMelding = "Annen advarsel")))
    }

    @Test
    fun `beregning med innhold skjules ikke som manglende testperson`() {
        val calculation = requireNotNull(rest.beregning)
        assertFalse(isMissingTestPersonMismatch("dev-gcp", soap, rest.copy(beregning = calculation.copy(gjelderId = "12345678910"))))
        assertFalse(isMissingTestPersonMismatch("dev-gcp", soap, rest.copy(beregning = calculation.copy(datoBeregnet = "2026-01-20"))))
        assertFalse(isMissingTestPersonMismatch("dev-gcp", soap, rest.copy(beregning = calculation.copy(belopTotalt = BigDecimal.ONE))))
    }
}
