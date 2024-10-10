package utsjekk.task

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import libs.job.Scheduler
import libs.postgres.Postgres
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.task.Operator
import libs.task.SelectTime
import libs.task.Status.FAIL
import libs.task.Status.IN_PROGRESS
import libs.task.TaskDao
import libs.task.Tasks
import libs.utils.appLog
import libs.utils.secureLog
import utsjekk.LeaderElector
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

class TaskScheduler(
    private val strategies: List<TaskStrategy>,
    private val elector: LeaderElector,
) : Scheduler<TaskDao>(
    feedRPM = 120,
    errorCooldownMs = 100,
    context = Postgres.context + Dispatchers.IO,
) {
    override fun isLeader(): Boolean = runBlocking { elector.isLeader() }

    override suspend fun feed(): List<TaskDao> {
        appLog.info("Locking 'task' in feed")
//        val list = withLock("task") {
            appLog.warn("begin feed transaction")
            return transaction {
                appLog.warn("begin dao select")
                TaskDao
                    .select {
                        it.status = listOf(IN_PROGRESS, FAIL)
                        it.scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                    }.also {
                        appLog.info("Feeding scheduler with ${it.size} tasks")
                    }
            }.also {
                appLog.warn("end feed transaction")
            }
//        }
//        appLog.info("Unlocking 'task' in feed")
//        return list
    }

    override suspend fun task(fed: TaskDao) {
        try {
            appLog.info("Task with id ${fed.id} and kind ${fed.kind} locked for processing")
//            withLock(fed.id.toString()) {
                strategies.single { it.isApplicable(fed) }.execute(fed)
//            }
            appLog.info("Task with id ${fed.id} unlocked")
        } catch (cancel: CancellationException) {
            appLog.warn("task job is shutting down..")
            throw cancel
        }
    }

    override suspend fun onError(fed: TaskDao, err: Throwable) {
        secureLog.error("Ukjent feil oppstod ved uførelse av task. Se logger", err)
        Tasks.update(fed.id, FAIL, err.message) {
            Kind.valueOf(kind.name).retryStrategy(it)
        }
    }
}
