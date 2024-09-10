package utsjekk.iverksetting.utbetalingsoppdrag

import TestData
import no.nav.utsjekk.kontrakter.felles.Satstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.BehandlingId
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.SakId
import java.time.LocalDate

class UtbetalingsgeneratorTest {

    @Test
    fun `periode er idempotent`() {
        val sakId = SakId(RandomOSURId.generate())
        val behId1 = BehandlingId(RandomOSURId.generate())

        val andel1 = TestData.domain.andelData(LocalDate.of(2021, 2, 1), LocalDate.of(2021, 3, 31))
        assertTrue(andel1.periodeId == null && andel1.forrigePeriodeId == null)
        val oppdrag1 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = TestData.domain.behandlingsinformasjon(fagsakId = sakId, behandlingId = behId1),
            nyeAndeler = listOf(andel1),
            forrigeAndeler = listOf(),
            sisteAndelPerKjede = mapOf(),
        )

        val behId2 = BehandlingId(RandomOSURId.generate())
        val andel2 = TestData.domain.andelData(LocalDate.of(2021, 2, 1), LocalDate.of(2021, 3, 31))
        assertTrue(andel2.periodeId == null && andel2.forrigePeriodeId == null)
        val oppdrag2 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = TestData.domain.behandlingsinformasjon(fagsakId = sakId, behandlingId = behId2),
            nyeAndeler = listOf(andel2),
            forrigeAndeler = listOf(andel1.copy(periodeId = 0)),
            sisteAndelPerKjede = mapOf(andel1.stønadsdata.tilKjedenøkkel() to andel1),
        )

        assertEquals(true, oppdrag1.utbetalingsoppdrag.erFørsteUtbetalingPåSak)
        assertEquals(TestData.DEFAULT_FAGSYSTEM, oppdrag1.utbetalingsoppdrag.fagsystem)
        assertEquals(sakId.id, oppdrag1.utbetalingsoppdrag.saksnummer)
        assertEquals(null, oppdrag1.utbetalingsoppdrag.iverksettingId)
        assertEquals(TestData.DEFAULT_PERSONIDENT, oppdrag1.utbetalingsoppdrag.aktør)
        assertEquals(TestData.DEFAULT_SAKSBEHANDLER, oppdrag1.utbetalingsoppdrag.saksbehandlerId)
        assertEquals(TestData.DEFAULT_BESLUTTER, oppdrag1.utbetalingsoppdrag.beslutterId)
//        assertEquals(LocalDate.now(), oppdrag1.utbetalingsoppdrag.avstemmingstidspunkt)
        assertEquals(null, oppdrag1.utbetalingsoppdrag.brukersNavKontor)
        assertEquals(oppdrag1.utbetalingsoppdrag.utbetalingsperiode.size, 1)
        assertEquals(false, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].erEndringPåEksisterendePeriode)
        assertEquals(null, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].opphør)
        assertEquals(0L, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].periodeId)
        assertEquals(null, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].forrigePeriodeId)
//        assertEquals(LocalDate.now(), oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].vedtaksdato)
        assertEquals("DPORAS", oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].klassifisering)
        assertEquals(LocalDate.of(2021, 2, 1), oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].fom)
        assertEquals(LocalDate.of(2021, 3, 31), oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].tom)
        assertEquals(700.toBigDecimal(), oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].sats)
        assertEquals(Satstype.DAGLIG, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].satstype)
        assertEquals(TestData.DEFAULT_PERSONIDENT, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].utbetalesTil)
        assertEquals(behId1.id, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].behandlingId)
        assertEquals(null, oppdrag1.utbetalingsoppdrag.utbetalingsperiode[0].utbetalingsgrad)
    }
}
