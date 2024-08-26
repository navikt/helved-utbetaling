package utsjekk.iverksetting

import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.ApiError.Companion.serviceUnavailable
import utsjekk.featuretoggle.FeatureToggles
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.CoroutineContext

class IverksettingService(private val context: CoroutineContext, private val toggles: FeatureToggles) {
    suspend fun iverksett(iverksetting: Iverksetting) {
        val fagsystem = iverksetting.fagsak.fagsystem
        if (toggles.isDisabled(fagsystem)) {
            serviceUnavailable("Iverksetting er skrudd av for fagsystem $fagsystem")
        }

        withContext(context) {
            transaction {
                val now = LocalDateTime.now()

                IverksettingDao(
                    iverksetting.behandlingId,
                    iverksetting,
                    now,
                ).insert()

                IverksettingResultatDao(
                    fagsystem = iverksetting.fagsak.fagsystem,
                    sakId = iverksetting.sakId,
                    behandlingId = iverksetting.behandlingId,
                    iverksettingId = iverksetting.iverksettingId
                ).insert()

                TaskDao(
                    id = UUID.randomUUID(),
                    kind = Kind.Iverksetting,
                    payload = objectMapper.writeValueAsString(iverksetting),
                    status = Status.UNPROCESSED,
                    attempt = 0,
                    createdAt = now,
                    updatedAt = now,
                    scheduledFor = now.plusDays(1), // todo: set correct schedule
                    message = null,
                ).insert()
            }
        }
    }

    suspend fun utledStatus(
        client: Client,
        sakId: SakId,
        behandlingId: BehandlingId,
        iverksettingId: IverksettingId?
    ): IverksettStatus? {
        val result = withContext(context) {
            IverksettingResultatDao.select(1) {
                this.fagsystem = client.toFagsystem()
                this.sakId = sakId
                this.behandlingId = behandlingId
                this.iverksettingId = iverksettingId
            }.singleOrNull()
        }

        if (result == null) {
            return null
        }

        if (result.oppdragresultat != null) {
            return when (result.oppdragresultat.oppdragStatus) {
                OppdragStatus.LAGT_PÅ_KØ -> IverksettStatus.SENDT_TIL_OPPDRAG
                OppdragStatus.KVITTERT_OK -> IverksettStatus.OK
                OppdragStatus.OK_UTEN_UTBETALING -> IverksettStatus.OK_UTEN_UTBETALING
                else -> IverksettStatus.FEILET_MOT_OPPDRAG
            }
        }

        return when (result.tilkjentytelseforutbetaling) {
            null -> IverksettStatus.IKKE_PÅBEGYNT
            else -> IverksettStatus.SENDT_TIL_OPPDRAG
        }
    }
}

@JvmInline
value class Client(private val name: String) {
    fun toFagsystem(): Fagsystem =
        when (name) {
            "utsjekk" -> Fagsystem.DAGPENGER
            "tiltakspenger-vedtak" -> Fagsystem.TILTAKSPENGER
            "tilleggstønader" -> Fagsystem.TILLEGGSSTØNADER
            else -> error("mangler mapping mellom app ($name) og fagsystem")
        }
}

@JvmInline
value class SakId(val id: String)

@JvmInline
value class BehandlingId(val id: String)

@JvmInline
value class IverksettingId(val id: String)