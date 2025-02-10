package simulering.models.soap

import no.nav.utsjekk.kontrakter.felles.Personident
import org.junit.jupiter.api.Test
import simulering.models.rest.rest
import java.time.LocalDate
import kotlin.test.assertNull

class SimuleringMappingTest {

    @Test
    fun `setter ikke ref-felter for endringer`() {
        val request = rest.SimuleringRequest(
            fagområde = "TILLST",
            sakId = "200000263",
            personident = Personident("12489404263"),
            erFørsteUtbetalingPåSak = false,
            saksbehandler = "R154509",
            utbetalingsperioder = listOf(
                rest.Utbetalingsperiode(
                    periodeId = "3",
                    forrigePeriodeId = "2",
                    erEndringPåEksisterendePeriode = true,
                    klassekode = "TSTBASISP5-OP",
                    fom = LocalDate.of(2024, 6, 3),
                    tom = LocalDate.of(2024, 6, 3),
                    sats = 1772,
                    satstype = rest.SatsType.DAG,
                    opphør = rest.Opphør(LocalDate.of(2024, 6, 3)),
                    utbetalesTil = "12489404263",
                )
            )
        )

        val simulerBeregningRequest = soap.SimulerBeregningRequest.from(request)

        assertNull(simulerBeregningRequest.request.oppdrag.oppdragslinje[0].refDelytelseId)
        assertNull(simulerBeregningRequest.request.oppdrag.oppdragslinje[0].refFagsystemId)
    }
}
