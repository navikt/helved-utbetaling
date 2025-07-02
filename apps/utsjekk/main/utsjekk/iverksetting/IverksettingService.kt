package utsjekk.iverksetting

import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import models.kontrakter.felles.Fagsystem
import models.kontrakter.iverksett.IverksettStatus
import models.kontrakter.oppdrag.OppdragStatus
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.iverksetting.abetal.OppdragService
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import utsjekk.unavailable
import utsjekk.partition
import utsjekk.utbetaling.UtbetalingId
import java.util.UUID
import java.time.LocalDateTime

class IverksettingService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag>,
) {
    suspend fun valider(iverksetting: Iverksetting) {
        withContext(Jdbc.context) {
            transaction {
                IverksettingValidator.validerAtIverksettingIkkeAlleredeErMottatt(iverksetting)
                IverksettingValidator.validerAtIverksettingGjelderSammeSakSomForrigeIverksetting(iverksetting)
                IverksettingValidator.validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting)
                IverksettingValidator.validerAtForrigeIverksettingErFerdigIverksattMotOppdrag(iverksetting)
            }
        }
    }

    suspend fun iverksett(iverksetting: Iverksetting) {
        withContext(Jdbc.context) {
            transaction {
                val now = LocalDateTime.now()
                val uid = UtbetalingId(UUID.randomUUID())
                IverksettingDao(iverksetting, now).insert(uid)
                IverksettingResultater.opprett(iverksetting, uid, resultat = null)
                
                when (val oppdrag = OppdragService.create(iverksetting)) {
                    null -> {
                        IverksettingResultater.oppdater(
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
}
