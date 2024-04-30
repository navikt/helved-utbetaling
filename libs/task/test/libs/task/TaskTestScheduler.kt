package libs.task

import kotlinx.coroutines.CoroutineScope
import libs.job.Scheduler
import libs.postgres.coroutines.transaction
import libs.utils.secureLog
import java.util.concurrent.atomic.AtomicLong

class TaskTestScheduler(scope: CoroutineScope) : Scheduler<TaskDao>(
    feedRPM = 600,
    errorCooldownMs = 500,
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
        val size = counter.incrementAndGet()

        transaction {
            feeded.update(Status.KLAR_TIL_PLUKK)
        }

        if (size % 20_000 == 0L) {
            println("Tasks completed: ${size / 1000}K")
        }
    }
}
