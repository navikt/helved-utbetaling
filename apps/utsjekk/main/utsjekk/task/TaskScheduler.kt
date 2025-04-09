package utsjekk.task

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import libs.job.Scheduler
import libs.postgres.Jdbc
import utsjekk.task.exponentialSec
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.task.Operator
import libs.task.SelectTime
import libs.task.Status.FAIL
import libs.task.Status.IN_PROGRESS
import libs.task.TaskDao
import libs.task.Tasks
import libs.utils.secureLog
import utsjekk.appLog
import java.time.LocalDateTime

//class TaskScheduler(
//    private val strategies: List<TaskStrategy>,
//    private val elector: LeaderElector,
//    private val metrics: MeterRegistry,
//) : Scheduler<TaskDao>(
//    feedRPM = 120,
//    errorCooldownMs = 100,
//    context = Jdbc.context + Dispatchers.IO,
//) {
//    private val meterTags = listOf(Tag.of("name", "task"))
//
//    override fun isLeader(): Boolean = runBlocking {
//        elector.isLeader()
//    }
//
//    override suspend fun feed(): List<TaskDao> {
//        metrics.counter("scheduler_feed_rpm", meterTags).increment()
//
//        return withLock("task") {
//            transaction {
//                TaskDao.select {
//                    it.status = listOf(IN_PROGRESS, FAIL)
//                    it.scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
//                }.also {
//                    metrics.counter("scheduler_feed_size", meterTags).increment(it.size.toDouble())
//                    appLog.debug("Feeding scheduler with ${it.size} tasks")
//                }
//            }
//        }
//    }
//
//    override suspend fun task(fed: TaskDao) {
//        withLock(fed.id.toString()) {
//            strategies.singleOrNull { it.isApplicable(fed) }?.execute(fed) ?: error("fant ingen strategy for ${fed.kind}")
//        }
//        metrics.counter("scheduler_feed_result", meterTags + Tag.of("result", "ok")).increment()
//    }
//
//    override suspend fun onError(fed: TaskDao, err: Throwable) {
//        secureLog.error("Ukjent feil oppstod ved uførelse av task. Se logger", err)
//        Tasks.update(fed.id, FAIL, err.message, TaskDao::exponentialSec)
//        metrics.counter("scheduler_feed_result", meterTags + Tag.of("result", "error")).increment()
//    }
//}
