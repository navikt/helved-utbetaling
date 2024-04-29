package utsjekk

import libs.job.Scheduler
import libs.postgres.transaction
import libs.task.Status
import libs.task.TaskDao
import libs.utils.appLog
import libs.utils.secureLog
import javax.sql.DataSource

class TaskScheduler(
    private val postgres: DataSource
) : Scheduler<TaskDao>(
    feedRPM = 60,
    errorCooldownMs = 500,
) {

    override fun feed(): List<TaskDao> =
        runCatching {
            postgres.transaction { con ->
                TaskDao.findBy(Status.KLAR_TIL_PLUKK, con)
            }
        }.getOrDefault(emptyList())

    override fun onError(err: Throwable) {
        secureLog.error("Feil oppstod ved uførelse av task", err)
    }

    override fun task(feeded: TaskDao) {
        appLog.info("Forsøker å utføre task: $feeded")
    }
}
