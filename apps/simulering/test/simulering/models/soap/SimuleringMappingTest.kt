package simulering.models.soap

import no.nav.utsjekk.kontrakter.felles.Personident
import org.junit.jupiter.api.Test
import simulering.models.rest.rest
import java.time.LocalDate
import kotlin.test.assertEquals
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
                    utbetalesTil = "12489404263"
                )
            )
        )

        val simulerBeregningRequest = soap.SimulerBeregningRequest.from(request)

        assertNull(simulerBeregningRequest.request.oppdrag.oppdragslinje[0].refDelytelseId)
        assertNull(simulerBeregningRequest.request.oppdrag.oppdragslinje[0].refFagsystemId)
    }

    @Test
    fun `setter fom-dato på simuleringsperiode til opphørsdato når opphørsdato er tidligere enn tidligste periode`() {
        val opphørsdato = LocalDate.of(2024, 4, 3)
        val periodeDato = LocalDate.of(2024, 6, 3)
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
                    fom = periodeDato,
                    tom = periodeDato,
                    sats = 1772,
                    satstype = rest.SatsType.DAG,
                    opphør = rest.Opphør(opphørsdato),
                    utbetalesTil = "12489404263"
                )
            )
        )

        val simulerBeregningRequest = soap.SimulerBeregningRequest.from(request)

        assertEquals(opphørsdato, simulerBeregningRequest.request.simuleringsPeriode.datoSimulerFom)
    }

    @Test
    fun `setter fom-dato på simuleringsperiode til tidligste periode når opphørsdato er senere enn tidligste periode`() {
        val opphørsdato = LocalDate.of(2024, 6, 15)
        val periodeFom = LocalDate.of(2024, 6, 3)
        val periodeTom = LocalDate.of(2024, 6, 30)
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
                    fom = periodeFom,
                    tom = periodeTom,
                    sats = 1772,
                    satstype = rest.SatsType.DAG,
                    opphør = rest.Opphør(opphørsdato),
                    utbetalesTil = "12489404263"
                )
            )
        )

        val simulerBeregningRequest = soap.SimulerBeregningRequest.from(request)

        assertEquals(periodeFom, simulerBeregningRequest.request.simuleringsPeriode.datoSimulerFom)
    }

    @Test
    fun `setter fom-dato på simuleringsperiode tidligste periode når det ikke finnes opphør`() {
        val periodeDato = LocalDate.of(2024, 6, 3)
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
                    fom = periodeDato,
                    tom = periodeDato,
                    sats = 1772,
                    satstype = rest.SatsType.DAG,
                    utbetalesTil = "12489404263",
                    opphør = null,
                )
            )
        )

        val simulerBeregningRequest = soap.SimulerBeregningRequest.from(request)

        assertEquals(periodeDato, simulerBeregningRequest.request.simuleringsPeriode.datoSimulerFom)
    }
}