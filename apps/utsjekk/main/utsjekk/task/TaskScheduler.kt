package utsjekk.task

import io.ktor.client.*
import io.ktor.client.request.*
import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.task.Operator
import libs.task.SelectTime
import libs.task.Status
import libs.task.TaskDao
import libs.utils.secureLog
import utsjekk.oppdrag.OppdragClient
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class TaskScheduler(
    private val oppdrag: OppdragClient,
    context: CoroutineContext,
) : Scheduler<TaskDao>(
    feedRPM = 60,
    errorCooldownMs = 100,
    context = context,
) {
    override suspend fun feed(): List<TaskDao> =
        withLock("task") {
            transaction {
                TaskDao.select(
                    status = listOf(Status.UNPROCESSED),
                    scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                )
            }
        }

    override suspend fun task(feeded: TaskDao) {
//        oppdrag.post("http://utsjekk-oppdrag/oppdrag") {
//            bearerAuth()
//        }
    }

    override suspend fun onError(err: Throwable) {
        secureLog.error("Feil oppstod ved uførelse av task", err)
    }
}
