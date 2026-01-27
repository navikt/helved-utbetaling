package utsjekk.iverksetting

import TestData
import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utsjekk.utbetaling.UtbetalingId
import java.util.*

class IverksettingResultaterTest {

    @Test
    fun `kan opprette tomt resultat`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        IverksettingService.saveEmptyResultat(iverksetting, UtbetalingId(UUID.randomUUID()), null)

        val resultater = transaction {
            IverksettingResultatDao.select {
                this.fagsystem = iverksetting.fagsak.fagsystem
                this.iverksettingId = iverksetting.iverksettingId
                this.behandlingId = iverksetting.behandlingId
                this.sakId = iverksetting.sakId
            }
        }

        assertEquals(1, resultater.size)
        assertNull(resultater.single().oppdragResultat)
        assertNull(resultater.single().tilkjentYtelseForUtbetaling)
    }

    @Test
    fun `kan hente resultat for iverksetting`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()

        assertThrows<IllegalStateException> {
            IverksettingService.hent(iverksetting)
        }

        transaction {
            IverksettingResultatDao(
                fagsystem = iverksetting.fagsak.fagsystem,
                sakId = iverksetting.sakId,
                behandlingId = iverksetting.behandlingId,
                iverksettingId = iverksetting.iverksettingId,
                tilkjentYtelseForUtbetaling = null,
                oppdragResultat = OppdragResultat(OppdragStatus.KVITTERT_OK)
            ).insert(UtbetalingId(UUID.randomUUID()))
        }

        val resultat = IverksettingService.hent(iverksetting)
        assertEquals(OppdragResultat(OppdragStatus.KVITTERT_OK).oppdragStatus, resultat.oppdragResultat!!.oppdragStatus)
    }

    @Test
    fun `kan hente resultat for forrige iverksetting`() = runTest(TestRuntime.context) {
        val forrige = TestData.domain.iverksetting(
            iverksettingId = IverksettingId("awesome splendid spectacular")
        )

        val nyeste = TestData.domain.iverksetting(
            forrigeBehandlingId = forrige.behandlingId,
            forrigeIverksettingId = forrige.iverksettingId,
            sakId = forrige.sakId
        )

        assertThrows<IllegalStateException> {
            IverksettingService.hentForrige(nyeste)
        }

        transaction {
            IverksettingResultatDao(
                fagsystem = forrige.fagsak.fagsystem,
                sakId = forrige.sakId,
                behandlingId = forrige.behandlingId,
                iverksettingId = forrige.iverksettingId,
                tilkjentYtelseForUtbetaling = null,
                oppdragResultat = OppdragResultat(OppdragStatus.KVITTERT_OK)
            ).insert(UtbetalingId(UUID.randomUUID()))
        }

        val resultat = IverksettingService.hentForrige(nyeste)
        assertEquals(OppdragResultat(OppdragStatus.KVITTERT_OK).oppdragStatus, resultat.oppdragResultat!!.oppdragStatus)

    }

    @Test
    fun `kan oppdatere resultat for iverksetting`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()

        transaction {
            IverksettingResultatDao(
                fagsystem = iverksetting.fagsak.fagsystem,
                sakId = iverksetting.sakId,
                behandlingId = iverksetting.behandlingId,
                iverksettingId = iverksetting.iverksettingId,
                tilkjentYtelseForUtbetaling = null,
                oppdragResultat = null,
            ).insert(UtbetalingId(UUID.randomUUID()))
        }

        assertNull(IverksettingService.hent(iverksetting).oppdragResultat)

        val resultat = OppdragResultat(OppdragStatus.KVITTERT_OK)
        IverksettingService.oppdater(iverksetting, resultat)

        assertEquals(resultat, IverksettingService.hent(iverksetting).oppdragResultat)
    }

    @Test
    fun `kan oppdatere tilkjent ytelse for iverksetting`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()

        transaction {
            IverksettingResultatDao(
                fagsystem = iverksetting.fagsak.fagsystem,
                sakId = iverksetting.sakId,
                behandlingId = iverksetting.behandlingId,
                iverksettingId = iverksetting.iverksettingId,
                tilkjentYtelseForUtbetaling = null,
                oppdragResultat = null,
            ).insert(UtbetalingId(UUID.randomUUID()))
        }

        assertNull(IverksettingService.hent(iverksetting).tilkjentYtelseForUtbetaling)

        val tilkjentYtelse = TestData.domain.enTilkjentYtelse(emptyList())
        IverksettingService.oppdater(iverksetting, tilkjentYtelse)

        assertEquals(tilkjentYtelse, IverksettingService.hent(iverksetting).tilkjentYtelseForUtbetaling)
    }
}
