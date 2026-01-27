package utsjekk.iverksetting

import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import models.badRequest
import models.conflict
import models.kontrakter.Fagsystem
import models.locked
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import utsjekk.partition
import java.time.LocalDateTime
import java.util.*

class IverksettingService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag>,
) {
    suspend fun valider(iverksetting: Iverksetting) {
        withContext(Jdbc.context) {
            transaction {
                validerAtIverksettingIkkeAlleredeErMottatt(iverksetting)
                validerAtIverksettingGjelderSammeSakSomForrigeIverksetting(iverksetting)
                validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting)
                validerAtForrigeIverksettingErFerdigIverksattMotOppdrag(iverksetting)
            }
        }
    }

    suspend fun iverksett(iverksetting: Iverksetting) {
        withContext(Jdbc.context) {
            transaction {
                val now = LocalDateTime.now()
                val uid = utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())
                IverksettingDao(iverksetting, now).insert(uid)
                saveEmptyResultat(iverksetting, uid, resultat = null)
                
                when (val oppdrag = IverksettingOppdragService.create(iverksetting)) {
                    null -> {
                        oppdater(
                            iverksetting = iverksetting,
                            resultat = OppdragResultat(OppdragStatus.OK_UTEN_UTBETALING),
                        )
                    }
                    else -> {
                        oppdragProducer.send(uid.id.toString(), oppdrag, partition(uid.id.toString()))
                    }
                }
            }
        }
    }

    suspend fun utledStatus(
        fagsystem: Fagsystem,
        sakId: SakId,
        behandlingId: BehandlingId,
        iverksettingId: IverksettingId?,
    ): IverksettStatus? {
        val result = withContext(Jdbc.context) {
            transaction {
                IverksettingResultatDao.select(1) {
                    this.fagsystem = fagsystem // client.toFagsystem()
                    this.sakId = sakId
                    this.behandlingId = behandlingId
                    this.iverksettingId = iverksettingId
                }.singleOrNull()
            }
        }

        if (result == null) {
            return null
        }

        if (result.oppdragResultat != null) {
            return when (result.oppdragResultat.oppdragStatus) {
                OppdragStatus.LAGT_PÅ_KØ -> IverksettStatus.SENDT_TIL_OPPDRAG
                OppdragStatus.KVITTERT_OK -> IverksettStatus.OK
                OppdragStatus.OK_UTEN_UTBETALING -> IverksettStatus.OK_UTEN_UTBETALING
                else -> IverksettStatus.FEILET_MOT_OPPDRAG
            }
        }

        return when (result.tilkjentYtelseForUtbetaling) {
            null -> IverksettStatus.IKKE_PÅBEGYNT
            else -> IverksettStatus.SENDT_TIL_OPPDRAG
        }
    }

    suspend fun hentSisteMottatte(
        sakId: SakId,
        fagsystem: Fagsystem,
    ): Iverksetting? = transaction {
        IverksettingDao.select {
            this.sakId = sakId
            this.fagsystem = fagsystem
        }.maxByOrNull { it.mottattTidspunkt }?.data
    }

    companion object {
        suspend fun saveEmptyResultat(
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
}
