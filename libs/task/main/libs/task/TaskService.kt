package libs.task

import libs.postgres.concurrency.transaction
import libs.utils.secureLog
import javax.sql.DataSource

class TaskService(private val postgres: DataSource) {

    private fun <T> tryInto(data: T) = Ressurs.success(data)

    suspend fun finnAntallTaskerSomKreverOppfølging(): Ressurs<Long> {
        val data = runCatching {
            transaction {
                TaskDao.countBy(listOf(Status.MANUELL_OPPFØLGING))
            }
        }

        return data.fold(::tryInto) { err ->
            val msg = "Henting av antall tasker som krever oppfølging feilet"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
    }

    fun finnAntallTaskerMedStatusFeiletOgManuellOppfølging(): Ressurs<TaskerMedStatusFeiletOgManuellOppfølging> {
        TODO()
    }

    fun hentTasksForCallId(
        callId: String,
        saksbehandlerId: String
    ): Ressurs<List<TaskDto>>? {
        TODO()
    }

    fun hentTasksSomErFerdigNåMenFeiletFør(
        brukernavn: String
    ): Ressurs<List<TaskDto>>? {
        TODO()
    }

    fun hentTasks(
        statuses: List<Status>,
        saksbehandlerId: String,
        page: Int,
        type: String?
    ): Ressurs<List<TaskDto>> {
        TODO()
    }

    fun hentTaskLogg(
        id: Long,
        saksbehandlerId: String
    ): Ressurs<List<TaskloggDto>> {
        TODO()
    }

    fun rekjørTask(
        Id: Long,
        behandlerId: String
    ): Ressurs<String> {
        TODO()
    }

    fun rekjørTasks(
        status: Status,
        saksbehandlerId: String
    ): Ressurs<String> {
        TODO()
    }

    fun avvikshåndterTask(
        taskId: Long,
        avvikstype: Avvikstype,
        årsak: String,
        saksbehandlerId: String,
    ): Ressurs<String> {
        TODO()
    }

    fun kommenterTask(
        taskId: Long,
        kommentarDTO: KommentarDTO,
        saksbehandlerId: String
    ): Ressurs<String> {
        TODO()
    }

    fun hentTaskMedId(
        id: Long,
        saksbehandlerId: String
    ): Ressurs<TaskDto>? {
        TODO()
    }
}
