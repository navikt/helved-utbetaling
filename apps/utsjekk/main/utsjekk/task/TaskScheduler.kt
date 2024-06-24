package utsjekk.task

import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.utils.secureLog
import utsjekk.oppdrag.HttpError
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
                TaskDao.select(
                    status = listOf(Status.UNPROCESSED),
                    scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                )
            }
        }
    }

    override suspend fun task(feeded: TaskDao) {
        try {
            oppdrag.sendOppdrag(feeded.payload)
            Tasks.update(feeded.id, Status.COMPLETE, "")
        } catch (e: HttpError) {
            Tasks.update(feeded.id, Status.FAIL, e.message)
        }
    }

    override suspend fun onError(err: Throwable) {
        secureLog.error("Ukjent feil oppstod ved uførelse av task. Se logger", err)
    }
}
