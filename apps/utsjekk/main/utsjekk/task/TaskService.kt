package utsjekk.task

import libs.utils.secureLog
import utsjekk.ayncTransaction
import javax.sql.DataSource

class TaskService(private val postgres: DataSource) {

    private fun <T> tryInto(data: T) = Ressurs.success(data)

    suspend fun finnAntallTaskerSomKreverOppfølging(): Ressurs<Long> {
        val data = runCatching {
            postgres.ayncTransaction {
                TaskDao.countBy(listOf(Status.MANUELL_OPPFØLGING), it)
            }
        }

        return data.fold(::tryInto) { err ->
            val msg = "Henting av antall tasker som krever oppfølging feilet"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
    }

    suspend fun finnAntallTaskerMedStatusFeiletOgManuellOppfølging(): Ressurs<TaskerMedStatusFeiletOgManuellOppfølging> {
        TODO()
    }

    suspend fun hentTasksForCallId(
        callId: String,
        saksbehandlerId: String
    ): Ressurs<List<TaskDto>>? {
        TODO()
    }

    suspend fun hentTasksSomErFerdigNåMenFeiletFør(
        brukernavn: String
    ): Ressurs<List<TaskDto>>? {
        TODO()
    }

    suspend fun hentTasks(
        statuses: List<Status>,
        saksbehandlerId: String,
        page: Int,
        type: String?
    ): Ressurs<List<TaskDto>> {
        TODO()
    }

    suspend fun hentTaskLogg(
        id: Long,
        saksbehandlerId: String
    ): Ressurs<List<TaskloggDto>> {
        TODO()
    }

    suspend fun rekjørTask(
        Id: Long,
        behandlerId: String
    ): Ressurs<String> {
        TODO()
    }

    suspend fun rekjørTasks(
        status: Status,
        saksbehandlerId: String
    ): Ressurs<String> {
        TODO()
    }

    suspend fun avvikshåndterTask(
        taskId: Long,
        avvikstype: Avvikstype,
        årsak: String,
        saksbehandlerId: String,
    ): Ressurs<String> {
        TODO()
    }

    suspend fun kommenterTask(
        taskId: Long,
        kommentarDTO: KommentarDTO,
        saksbehandlerId: String
    ): Ressurs<String> {
        TODO()
    }

    suspend fun hentTaskMedId(
        id: Long,
        saksbehandlerId: String
    ): Ressurs<TaskDto>? {
        TODO()
    }
}
