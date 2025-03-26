package utsjekk.iverksetting.resultat

import libs.postgres.concurrency.transaction
import utsjekk.iverksetting.*

object IverksettingResultater {

    suspend fun opprett(
        iverksetting: Iverksetting,
        uid: utsjekk.utbetaling.UtbetalingId,
        resultat: OppdragResultat?,
    ): IverksettingResultatDao {
        return transaction {
            IverksettingResultatDao(
                fagsystem = iverksetting.fagsak.fagsystem,
                sakId = iverksetting.sakId,
                behandlingId = iverksetting.behandlingId,
                iverksettingId = iverksetting.iverksettingId,
                oppdragResultat = resultat,
            ).also {
                it.insert(uid)
            }
        }
    }

    suspend fun oppdater(iverksetting: Iverksetting, tilkjentYtelse: TilkjentYtelse) {
        transaction {
            hent(iverksetting)
                .copy(tilkjentYtelseForUtbetaling = tilkjentYtelse)
                .update()
        }
    }

    suspend fun oppdater(iverksetting: Iverksetting, resultat: OppdragResultat) {
        transaction {
            hent(iverksetting)
                .copy(oppdragResultat = resultat)
                .update()
        }
    }

    suspend fun hent(iverksetting: Iverksetting): IverksettingResultatDao {
        return transaction {
            IverksettingResultatDao.select(1) {
                this.iverksettingId = iverksetting.iverksettingId
                this.behandlingId = iverksetting.behandlingId
                this.sakId = iverksetting.sakId
                this.fagsystem = iverksetting.fagsak.fagsystem
            }.singleOrNull() ?: error(
                """
                Fant ikke iverksettingresultat for iverksetting med 
                    iverksettingId  ${iverksetting.iverksettingId}
                    behandlingId    ${iverksetting.behandlingId}
                    sakId           ${iverksetting.sakId}
                    fagsystem       ${iverksetting.fagsak.fagsystem}
                """.trimIndent()
            )
        }
    }

    suspend fun hent(utbetalingId: UtbetalingId): IverksettingResultatDao {
        return transaction {
            IverksettingResultatDao.select(1) {
                this.iverksettingId = utbetalingId.iverksettingId
                this.behandlingId = utbetalingId.behandlingId
                this.sakId = utbetalingId.sakId
                this.fagsystem = utbetalingId.fagsystem
            }.singleOrNull() ?: error(
                """
                Fant ikke iverksettingresultat for iverksetting med 
                    iverksettingId  ${utbetalingId.iverksettingId}
                    behandlingId    ${utbetalingId.behandlingId}
                    sakId           ${utbetalingId.sakId}
                    fagsystem       ${utbetalingId.fagsystem}
                """.trimIndent()
            )
        }
    }

    suspend fun hentForrige(iverksetting: Iverksetting): IverksettingResultatDao {
        return transaction {
            IverksettingResultatDao.select(1) {
                this.iverksettingId = iverksetting.behandling.forrigeIverksettingId
                this.behandlingId = iverksetting.behandling.forrigeBehandlingId
                this.sakId = iverksetting.sakId
                this.fagsystem = iverksetting.fagsak.fagsystem
            }.singleOrNull() ?: error(
                """
                Fant ikke forrige iverksettingresultat for iverksetting med 
                    forrigeIverksettingId  ${iverksetting.behandling.forrigeIverksettingId}
                    forrigeBehandlingId    ${iverksetting.behandling.forrigeBehandlingId}
                    sakId                  ${iverksetting.sakId}
                    fagsystem              ${iverksetting.fagsak.fagsystem}
                """.trimIndent()
            )
        }
    }

}