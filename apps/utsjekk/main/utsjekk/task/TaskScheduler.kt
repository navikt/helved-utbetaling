package utsjekk.task

import io.ktor.util.logging.*
import kotlinx.coroutines.CancellationException
import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.utils.appLog
import libs.utils.secureLog
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class TaskScheduler(
    private val strategies: List<TaskStrategy>,
    context: CoroutineContext,
) : Scheduler<TaskDao>(
        feedRPM = 120,
        errorCooldownMs = 100,
        context = context,
    ) {
    override suspend fun feed(): List<TaskDao> {
        withLock("task") {
            return transaction {
                TaskDao
                    .select {
                        it.status = listOf(Status.IN_PROGRESS, Status.FAIL)
                        it.scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                    }.also {
                        appLog.debug("Feeding scheduler with ${it.size} tasks")
                    }
            }
        }
    }

    override suspend fun task(fed: TaskDao) {
        appLog.info("Task with id ${fed.id} and kind ${fed.kind} locked for processing")
        withLock(fed.id.toString()) {
            try {
                strategies.single { it.isApplicable(fed) }.execute(fed)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Tasks.update(fed.id, Status.FAIL, e.message)
                secureLog.error(e)
            }
        }
        appLog.info("Task with id ${fed.id} unlocked")
    }

    override suspend fun onError(err: Throwable) {
        secureLog.error("Ukjent feil oppstod ved uførelse av task. Se logger", err)
    }
}
