package utsjekk.iverksetting

import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
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

        fun enTask(
            status: Status = Status.UNPROCESSED,
            createdAt: LocalDateTime = LocalDateTime.now(),
        ) = TaskDao(
            id = UUID.randomUUID(),
            kind = Kind.Iverksetting,
            payload = "some payload",
            status = status,
            attempt = 0,
            createdAt = createdAt,
            updatedAt = createdAt,
            scheduledFor = createdAt,
            message = null,
        )

        withContext(context) {

            transaction {
                IverksettingDao(
                    iverksetting.behandlingId,
                    iverksetting,
                    LocalDateTime.now(),
                ).insert()

                IverksettingResultatDao(
                    fagsystem = iverksetting.fagsak.fagsystem,
                    sakId = iverksetting.sakId,
                    behandlingId = iverksetting.behandlingId,
                    iverksettingId = iverksetting.iverksettingId
                ).insert()

                enTask().insert()
            }
        }
    }

    fun utledStatus(
        client: Client,
        sakId: SakId,
        behandlingId: BehandlingId,
        iverksettingId: IverksettingId? = null
    ): IverksettStatus {
        TODO("impl")
    }
}

@JvmInline
value class Client(private val name: String)

@JvmInline
value class SakId(private val id: String)

@JvmInline
value class BehandlingId(private val id: String)

@JvmInline
value class IverksettingId(private val id: String)