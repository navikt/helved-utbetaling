package utsjekk.task

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.logging.*
import kotlinx.coroutines.CancellationException
import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.iverksetting.*
import utsjekk.oppdrag.OppdragClient
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class TaskScheduler(
    private val oppdrag: OppdragClient,
    context: CoroutineContext,
) : Scheduler<TaskDao>(
    feedRPM = 120,
    errorCooldownMs = 100,
    context = context,
) {
    override suspend fun feed(): List<TaskDao> {
        withLock("task") {
            secureLog.debug("Feeding scheduler")
            return transaction {
                val conditions = TaskDao.Where(
                    status = listOf(Status.UNPROCESSED),
                    scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                )
                TaskDao.select(conditions)
            }
        }
    }

    override suspend fun task(fed: TaskDao) {
        try {
            val json = fed.payload

            suspend fun updateIverksetting(iverksetting: Iverksetting) {
                transaction {
                    val iverksettingResultatDao = IverksettingResultatDao.select(1) {
                        iverksettingId = iverksetting.iverksettingId
                        behandlingId = iverksetting.behandlingId
                        sakId = iverksetting.sakId
                        fagsystem = iverksetting.fagsak.fagsystem
                    }.singleOrNull() ?: error(
                        """
                        Fant ikke iverksettingresultat for iverksetting med 
                            iverksettingId  ${iverksetting.iverksettingId}
                            behandlingId    ${iverksetting.behandlingId}
                            sakId           ${iverksetting.sakId}
                            fagsystem       $iverksetting.fagsak.fagsystem
                        """.trimIndent()
                    )

                    iverksettingResultatDao.copy(
                        oppdragresultat = OppdragResultat(
                            oppdragStatus = OppdragStatus.LAGT_PÅ_KØ
                        )
                    ).update()
                }
            }

            when (fed.kind) {
                Kind.Iverksetting -> updateIverksetting(objectMapper.readValue<Iverksetting>(json))
                Kind.Avstemming -> TODO("not implemented")
            }

            oppdrag.sendOppdrag(fed.payload)
            Tasks.update(fed.id, Status.COMPLETE, "")
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Tasks.update(fed.id, Status.FAIL, e.message)
            secureLog.error(e)
        }
    }

    override suspend fun onError(err: Throwable) {
        secureLog.error("Ukjent feil oppstod ved uførelse av task. Se logger", err)
    }
}
