package utsjekk.task

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import libs.job.Scheduler
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.task.Operator
import libs.task.SelectTime
import libs.task.Status.FAIL
import libs.task.Status.IN_PROGRESS
import libs.task.TaskDao
import libs.task.Tasks
import libs.utils.secureLog
import utsjekk.LeaderElector
import utsjekk.appLog
import java.time.LocalDateTime

class TaskScheduler(
    private val strategies: List<TaskStrategy>,
    private val elector: LeaderElector,
) : Scheduler<TaskDao>(
    feedRPM = 120,
    errorCooldownMs = 100,
    context = Jdbc.context + Dispatchers.IO,
) {
    override fun isLeader(): Boolean = runBlocking { elector.isLeader() }

    override suspend fun feed(): List<TaskDao> {
        return withLock("task") {
            transaction {
                TaskDao.select {
                    it.status = listOf(IN_PROGRESS, FAIL)
                    it.scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                }.also {
                    appLog.info("Feeding scheduler with ${it.size} tasks")
                }
            }
        }
    }

    override suspend fun task(fed: TaskDao) {
        withLock(fed.id.toString()) {
            strategies.single { it.isApplicable(fed) }.execute(fed)
        }
    }

    override suspend fun onError(fed: TaskDao, err: Throwable) {
        secureLog.error("Ukjent feil oppstod ved uførelse av task. Se logger", err)
        Tasks.update(fed.id, FAIL, err.message) {
            Kind.valueOf(kind.name).retryStrategy(it)
        }
    }
}
