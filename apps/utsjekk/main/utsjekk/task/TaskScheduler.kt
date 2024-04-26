package utsjekk.task

import libs.job.Scheduler
import libs.postgres.transaction
import libs.utils.appLog
import libs.utils.secureLog
import javax.sql.DataSource

class TaskScheduler(
    private val postgres: DataSource
) : Scheduler<TaskDao.Task>(
    feedRPM = 60,
    errorCooldownMs = 500,
) {

    override fun feed(): List<TaskDao.Task> =
        runCatching {
            postgres.transaction { con ->
                TaskDao.findBy(Status.KLAR_TIL_PLUKK, con)
            }
        }.getOrDefault(emptyList())

    override fun onError(err: Throwable) {
        secureLog.error("Feil oppstod ved uførelse av task", err)
    }

    override fun task(feeded: TaskDao.Task) {
        appLog.info("Forsøker å utføre task: $feeded")
    }
}
