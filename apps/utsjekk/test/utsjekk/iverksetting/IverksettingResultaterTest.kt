package utsjekk.iverksetting

import TestData
import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IverksettingResultaterTest {

    @Test
    fun `kan opprette tomt resultat`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        IverksettingResultater.opprett(iverksetting, null)

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
            IverksettingResultater.hent(iverksetting)
        }

        transaction {
            IverksettingResultatDao(
                fagsystem = iverksetting.fagsak.fagsystem,
                sakId = iverksetting.sakId,
                behandlingId = iverksetting.behandlingId,
                iverksettingId = iverksetting.iverksettingId,
                tilkjentYtelseForUtbetaling = null,
                oppdragResultat = OppdragResultat(OppdragStatus.KVITTERT_OK)
            ).insert()
        }

        val resultat = IverksettingResultater.hent(iverksetting)
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
            IverksettingResultater.hentForrige(nyeste)
        }

        transaction {
            IverksettingResultatDao(
                fagsystem = forrige.fagsak.fagsystem,
                sakId = forrige.sakId,
                behandlingId = forrige.behandlingId,
                iverksettingId = forrige.iverksettingId,
                tilkjentYtelseForUtbetaling = null,
                oppdragResultat = OppdragResultat(OppdragStatus.KVITTERT_OK)
            ).insert()
        }

        val resultat = IverksettingResultater.hentForrige(nyeste)
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
            ).insert()
        }

        assertNull(IverksettingResultater.hent(iverksetting).oppdragResultat)

        val resultat = OppdragResultat(OppdragStatus.KVITTERT_OK)
        IverksettingResultater.oppdater(iverksetting, resultat)

        assertEquals(resultat, IverksettingResultater.hent(iverksetting).oppdragResultat)
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
            ).insert()
        }

        assertNull(IverksettingResultater.hent(iverksetting).tilkjentYtelseForUtbetaling)

        val tilkjentYtelse = TestData.domain.enTilkjentYtelse(emptyList())
        IverksettingResultater.oppdater(iverksetting, tilkjentYtelse)

        assertEquals(tilkjentYtelse, IverksettingResultater.hent(iverksetting).tilkjentYtelseForUtbetaling)
    }
}
