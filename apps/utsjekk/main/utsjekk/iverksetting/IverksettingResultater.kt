package utsjekk.iverksetting

import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.Fagsystem

object IverksettingResultater {

    suspend fun single(
        iverksettingId: IverksettingId?,
        behandlingId: BehandlingId,
        sakId: SakId,
        fagsystem: Fagsystem
    ): IverksettingResultatDao {
        return transaction {
            IverksettingResultatDao.select(1) {
                this.iverksettingId = iverksettingId
                this.behandlingId = behandlingId
                this.sakId = sakId
                this.fagsystem = fagsystem
            }.singleOrNull() ?: error(
                """
                Fant ikke iverksettingresultat for iverksetting med 
                    iverksettingId  $iverksettingId
                    behandlingId    $behandlingId
                    sakId           $sakId
                    fagsystem       $fagsystem
                """.trimIndent()
            )
        }
    }

}