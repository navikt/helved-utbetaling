package utsjekk.task

import io.ktor.util.logging.error
import kotlinx.coroutines.CancellationException
import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.utils.secureLog
import utsjekk.task.strategies.IverksettingStrategy
import utsjekk.task.strategies.SjekkStatusStrategy
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class TaskScheduler(
    private val iverksettingStrategy: IverksettingStrategy,
    private val sjekkStatusStrategy: SjekkStatusStrategy,
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
                TaskDao.select {
                    it.status = listOf(Status.IN_PROGRESS, Status.FAIL)
                    it.scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                }
            }
        }
    }

    override suspend fun task(fed: TaskDao) {
        try {
            when (fed.kind) {
                Kind.Iverksetting -> iverksettingStrategy.execute(fed)
                Kind.Avstemming -> TODO("not implemented")
                Kind.SjekkStatus -> sjekkStatusStrategy.execute(fed)
            }
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
