package utsjekk.iverksetting

import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.badRequest
import utsjekk.conflict
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.locked

object IverksettingValidator {

    suspend fun validerAtIverksettingGjelderSammeSakSomForrigeIverksetting(iverksetting: Iverksetting) {
        if (iverksetting.behandling.forrigeBehandlingId == null) {
            return
        }

        val forrigeIverksetting = IverksettingDao.select {
            this.sakId = iverksetting.sakId
            this.behandlingId = iverksetting.behandling.forrigeBehandlingId
            this.iverksettingId = iverksetting.behandling.forrigeIverksettingId
            this.fagsystem = iverksetting.fagsak.fagsystem
        }.firstOrNull()

        if (forrigeIverksetting == null) {
            badRequest(
                """
                    Fant ikke iverksetting med sakId ${iverksetting.sakId} 
                    og behandlingId ${iverksetting.behandling.forrigeBehandlingId} 
                    og iverksettingId ${iverksetting.behandling.forrigeIverksettingId}
                """.trimIndent()
            )
        }
    }

    suspend fun validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting: Iverksetting) {
        val sisteMottatteIverksetting = IverksettingDao.select {
            this.sakId = iverksetting.sakId
            this.fagsystem = iverksetting.fagsak.fagsystem
        }.maxByOrNull { it.mottattTidspunkt }?.data
        // TODO: Skriv om til if/else
        sisteMottatteIverksetting?.let {
            if (it.behandlingId != iverksetting.behandling.forrigeBehandlingId ||
                it.behandling.iverksettingId != iverksetting.behandling.forrigeIverksettingId
            ) {
                badRequest(
                    """
                        Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken. 
                        BehandlingId/IverksettingId forrige iverksetting: ${iverksetting.behandling.forrigeBehandlingId}/${iverksetting.behandling.forrigeIverksettingId}, 
                        behandlingId/iverksettingId siste mottatte iverksetting: ${it.behandlingId}/${it.behandling.iverksettingId}
                    """.trimIndent(),
                )
            }
        } ?: run {
            if (iverksetting.behandling.forrigeBehandlingId != null || iverksetting.behandling.forrigeIverksettingId != null) {
                badRequest(
                    """
                        Det er ikke registrert noen tidligere iverksettinger på saken, men forrigeIverksetting er satt 
                        til behandling ${iverksetting.behandling.forrigeBehandlingId}/iverksetting ${iverksetting.behandling.forrigeIverksettingId}
                    """.trimIndent(),
                )
            }
        }
    }

    suspend fun validerAtForrigeIverksettingErFerdigIverksattMotOppdrag(iverksetting: Iverksetting) {
        if (iverksetting.behandling.forrigeBehandlingId == null) {
            return
        }

        val forrigeResultat = IverksettingResultatDao.select {
            this.fagsystem = iverksetting.fagsak.fagsystem
            this.sakId = iverksetting.sakId
            this.behandlingId = iverksetting.behandling.forrigeBehandlingId
            this.iverksettingId = iverksetting.behandling.forrigeIverksettingId
        }.firstOrNull()

        val kvittertOk = forrigeResultat?.oppdragResultat?.oppdragStatus in listOf(
            OppdragStatus.KVITTERT_OK,
            OppdragStatus.OK_UTEN_UTBETALING,
        )

        if (!kvittertOk) {
            locked("Forrige iverksetting er ikke ferdig iverksatt mot Oppdragssystemet")
        }
    }

    suspend fun validerAtIverksettingIkkeAlleredeErMottatt(iverksetting: Iverksetting) {
        val hentetIverksetting = IverksettingDao.select {
            this.fagsystem = iverksetting.fagsak.fagsystem
            this.sakId = iverksetting.fagsak.fagsakId
            this.behandlingId = iverksetting.behandling.behandlingId
            this.iverksettingId = iverksetting.behandling.iverksettingId
        }.firstOrNull()

        if (hentetIverksetting != null) {
            conflict("Iverksettingen er allerede mottatt")
        }
    }
}
