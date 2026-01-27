package utsjekk.simulering

import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import models.badRequest
import models.conflict
import utsjekk.iverksetting.OppdragStatus
import utsjekk.iverksetting.IverksettingService
import utsjekk.iverksetting.UtbetalingId
import utsjekk.iverksetting.behandlingId

class SimuleringService(private val iverksettinger: IverksettingService) {

    suspend fun valider(simulering: domain.Simulering) {
        withContext(Jdbc.context) {
            forrigeIverksettingSkalVæreFerdigstilt(simulering)
            forrigeIverksettingErLikSisteMottatteIverksetting(simulering)
        }
    }

    private suspend fun forrigeIverksettingSkalVæreFerdigstilt(simulering: domain.Simulering) {
        simulering.forrigeIverksetting?.apply {
            val forrigeResultat = runCatching {
                IverksettingService.hent(
                    UtbetalingId(
                        fagsystem = simulering.behandlingsinformasjon.fagsystem,
                        sakId = simulering.behandlingsinformasjon.fagsakId,
                        behandlingId = this.behandlingId,
                        iverksettingId = this.iverksettingId,
                    )
                )
            }.getOrNull()

            val ferdigstilteStatuser = listOf(OppdragStatus.KVITTERT_OK, OppdragStatus.OK_UTEN_UTBETALING)
            if (!ferdigstilteStatuser.contains(forrigeResultat?.oppdragResultat?.oppdragStatus)) {
                conflict("Forrige iverksetting er ikke ferdig iverksatt mot Oppdragssystemet")
            }
        }
    }

    private suspend fun forrigeIverksettingErLikSisteMottatteIverksetting(simulering: domain.Simulering) {
        val sisteMottatteIverksetting = iverksettinger.hentSisteMottatte(
            sakId = simulering.behandlingsinformasjon.fagsakId,
            fagsystem = simulering.behandlingsinformasjon.fagsystem,
        )

        if (sisteMottatteIverksetting != null) {
            if (sisteMottatteIverksetting.behandlingId != simulering.forrigeIverksetting?.behandlingId ||
                sisteMottatteIverksetting.behandling.iverksettingId != simulering.forrigeIverksetting.iverksettingId
            ) {
                badRequest(
                    "Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken. BehandlingId/IverksettingId forrige" +
                            " iverksetting: ${simulering.forrigeIverksetting?.behandlingId}/${simulering.forrigeIverksetting?.iverksettingId}," +
                            " behandlingId/iverksettingId siste mottatte iverksetting: ${sisteMottatteIverksetting.behandlingId}/${sisteMottatteIverksetting.behandling.iverksettingId}",
                )
            }
        } else {
            if (simulering.forrigeIverksetting != null) {
                badRequest(
                    "Det er ikke registrert noen tidligere iverksettinger på saken, men forrigeIverksetting er satt til " +
                            "behandling ${simulering.forrigeIverksetting.behandlingId}/iverksetting " +
                            "${simulering.forrigeIverksetting.iverksettingId}",
                )
            }
        }
    }

}
