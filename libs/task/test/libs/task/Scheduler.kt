package libs.task

import kotlinx.coroutines.CoroutineScope
import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.utils.secureLog
import java.util.concurrent.atomic.AtomicLong

class Scheduler(scope: CoroutineScope) : Scheduler<TaskDao>(
    feedRPM = 60,
    errorCooldownMs = 100,
    scope = scope,
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
