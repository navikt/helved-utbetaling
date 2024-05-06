package libs.task

import libs.postgres.concurrency.transaction
import libs.utils.appLog
import libs.utils.secureLog
import javax.sql.DataSource

class TaskService(private val postgres: DataSource) {

    private fun <T> tryInto(data: T) = Ressurs.success(data)

    suspend fun finnAntallTaskerSomKreverOppfølging(): Ressurs<Long> {
        val result = runCatching {
            transaction {
                TaskDao.countBy(Status.MANUELL_OPPFØLGING)
            }
        }

        return result.fold(::tryInto) { err ->
            val msg = "Feilet ved henting av antall tasker som krever oppfølging"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
    }

    suspend fun finnAntallTaskerMedStatusFeiletOgManuellOppfølging(): Ressurs<TaskerMedStatusFeiletOgManuellOppfølging> {
        val result = runCatching {
            transaction {
                TaskerMedStatusFeiletOgManuellOppfølging(
                    antallFeilet = TaskDao.countBy(Status.FEILET),
                    antallManuellOppfølging = TaskDao.countBy(Status.MANUELL_OPPFØLGING)
                )
            }
        }

        return result.fold(::tryInto) { err ->
            val msg = "Feilet ved henting av antall tasker som har feilet eller satt til manuell oppfølging"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
    }

    // TODO: bytt ut med open telemetry sin (x_trace_id)
//    suspend fun hentTasksForCallId(
//        callId: String,
//        saksbehandlerId: String
//    ): Ressurs<List<TaskDto>>?

    suspend fun hentTasksSomErFerdigNåMenFeiletFør(
        brukernavn: String,
    ): Ressurs<List<TaskDto>> {
        appLog.info("$brukernavn henter oppgraver som er ferdige nå, men feilet før")

        val result = runCatching {
            transaction {
                val taskWithMetadata = TaskDao.finnTasksSomErFerdigNåMenFeiletFør().associateWith {
                    TaskLoggDao.findMetadataBy(listOf(it.id))
                }

                taskWithMetadata.map { (task, meta) ->
                    TaskDto.from(task, meta.singleOrNull())
                }
            }
        }

        return result.fold(::tryInto) { err ->
            val msg = "Feilet ved henting av oppgaver som er ferdig nå, men feilet før"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
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
    ): Ressurs<List<TaskLoggDto>> {
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
