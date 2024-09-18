package utsjekk.iverksetting

import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.ApiError.Companion.serviceUnavailable
import utsjekk.featuretoggle.FeatureToggles
import utsjekk.status.Kafka
import utsjekk.task.Kind
import utsjekk.task.Tasks
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class Iverksettinger(
    private val context: CoroutineContext,
    private val toggles: FeatureToggles,
    private val statusProducer: Kafka<StatusEndretMelding>,
) {
    suspend fun valider(iverksetting: Iverksetting) {
        withContext(context) {
            transaction {
                IverksettingValidator.validerAtIverksettingGjelderSammeSakSomForrigeIverksetting(iverksetting)
                IverksettingValidator.validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting)
                IverksettingValidator.validerAtForrigeIverksettingErFerdigIverksattMotOppdrag(iverksetting)
                IverksettingValidator.validerAtIverksettingIkkeAlleredeErMottatt(iverksetting)
            }
        }
    }

    suspend fun iverksett(iverksetting: Iverksetting) {
        val fagsystem = iverksetting.fagsak.fagsystem
        if (toggles.isDisabled(fagsystem)) {
            serviceUnavailable("Iverksetting er skrudd av for fagsystem $fagsystem")
        }

        withContext(context) {
            transaction {
                val now = LocalDateTime.now()

                IverksettingDao(iverksetting, now).insert()

                IverksettingResultater.opprett(iverksetting, resultat = null)

                Tasks.create(Kind.Iverksetting, iverksetting)
            }
        }
    }

    suspend fun publiserStatusmelding(iverksetting: Iverksetting) {
        val status = utledStatus(
            fagsystem = iverksetting.fagsak.fagsystem,
            sakId = iverksetting.sakId,
            behandlingId = iverksetting.behandlingId,
            iverksettingId = iverksetting.iverksettingId
        )

        if (status != null) {
            val message = StatusEndretMelding(
                sakId = iverksetting.sakId.id,
                behandlingId = iverksetting.behandlingId.id,
                iverksettingId = iverksetting.iverksettingId?.id,
                fagsystem = iverksetting.fagsak.fagsystem,
                status = status
            )

            statusProducer.produce(
                key = iverksetting.søker.personident,
                value = message,
            )
        }
    }

    suspend fun utledStatus(
        fagsystem: Fagsystem,
        sakId: SakId,
        behandlingId: BehandlingId,
        iverksettingId: IverksettingId?
    ): IverksettStatus? {
        val result = withContext(context) {
            transaction {
                IverksettingResultatDao.select(1) {
                    this.fagsystem = fagsystem //client.toFagsystem()
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

    suspend fun hentSisteMottatte(sakId: SakId, fagsystem: Fagsystem): Iverksetting? =
        transaction {
            IverksettingDao.select {
                this.sakId = sakId
                this.fagsystem = fagsystem
            }.maxByOrNull { it.mottattTidspunkt }?.data
        }
}

@JvmInline
value class Client(private val name: String) {
    fun toFagsystem(): Fagsystem =
        when (name) {
            "utsjekk" -> Fagsystem.DAGPENGER
            "tiltakspenger-vedtak" -> Fagsystem.TILTAKSPENGER
            "tilleggsstonader-sak" -> Fagsystem.TILLEGGSSTØNADER
            else -> error("mangler mapping mellom app ($name) og fagsystem")
        }
}

@JvmInline
value class SakId(val id: String)

@JvmInline
value class BehandlingId(val id: String)

@JvmInline
value class IverksettingId(val id: String)