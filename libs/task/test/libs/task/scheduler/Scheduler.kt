package libs.task.scheduler

import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.task.Status
import libs.task.TaskDao
import libs.utils.secureLog
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

class Scheduler(context: CoroutineContext) : Scheduler<TaskDao>(
    feedRPM = 60,
    errorCooldownMs = 100,
    context = context,
) {

    private val counter = AtomicLong()

    override suspend fun feed(): List<TaskDao> =
        transaction {
            TaskDao.findBy(Status.UBEHANDLET)
        }

    override fun onError(err: Throwable) {
        secureLog.error("Feil oppstod ved uførelse av task", err)
    }

    override suspend fun task(feeded: TaskDao) {
        counter.incrementAndGet()

        transaction {
            feeded.update(Status.KLAR_TIL_PLUKK)
        }
    }
}