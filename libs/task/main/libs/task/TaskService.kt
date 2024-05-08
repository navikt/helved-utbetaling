package libs.task

import libs.postgres.concurrency.transaction
import libs.utils.appLog
import libs.utils.secureLog

object TaskService {

    private fun <T> tryInto(data: T) = Ressurs.success(data)

    suspend fun finnAntallTaskerSomKreverOppfølging(): Ressurs<Long> {
        val result = runCatching {
            transaction {
                TaskDao.countBy(Status.MANUELL_OPPFØLGING)
            }
        }

        return result.fold(::tryInto) { err ->
            val msg = "Feilet ved henting av antall tasks som krever oppfølging"
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
            val msg = "Feilet ved henting av antall tasks som har feilet eller satt til manuell oppfølging"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
    }

    suspend fun hentTasksSomErFerdigNåMenFeiletFør(
        navident: String,
    ): Ressurs<List<TaskDto>> {
        appLog.info("$navident henter tasks som er ferdige nå, men feilet før")

        val result = runCatching {
            transaction {
                TaskDao.finnTasksSomErFerdigNåMenFeiletFør()
                    .associateWith { task ->
                        TaskLogDao.findMetadataBy(listOf(task.id))
                    }
                    .map { (task, metadata) ->
                        TaskDto.from(task, metadata.singleOrNull())
                    }
            }
        }

        return result.fold(::tryInto) { err ->
            val msg = "Feilet ved henting av tasks som er ferdig nå, men feilet før"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
    }

    suspend fun hentTasks(
        statuser: List<Status>,
        navident: String,
        type: String?
    ): Ressurs<List<TaskDto>> {
        appLog.info("$navident henter ${type ?: ""} tasks med statuser $statuser")

        val result = runCatching {
            transaction {
                when (type) {
                    null -> TaskDao.findBy(statuser)
                    else -> TaskDao.findBy(statuser, type)
                }.associateWith { task ->
                    TaskLogDao.findMetadataBy(listOf(task.id))
                }.map { (task, metadata) ->
                    TaskDto.from(task, metadata.singleOrNull())
                }
            }
        }

        return result.fold(::tryInto) { err ->
            val msg = "Feilet ved henting av tasks med statuser $statuser"
            secureLog.error(msg, err)
            Ressurs.failure(msg, err)
        }
    }

    suspend fun getTaskLogs(
        id: Long,
        navident: String
    ): Ressurs<List<TaskLogDto>> {
        TODO()
    }

    suspend fun rekjørTask(
        Id: Long,
        navident: String
    ): Ressurs<String> {
        TODO()
    }

    suspend fun rekjørTasks(
        status: Status,
        navident: String
    ): Ressurs<String> {
        TODO()
    }

    suspend fun avvikshåndterTask(
        taskId: Long,
        avvikstype: Avvikstype,
        årsak: String,
        navident: String,
    ): Ressurs<String> {
        TODO()
    }

    suspend fun kommenterTask(
        taskId: Long,
        kommentarDTO: KommentarDTO,
        navident: String
    ): Ressurs<String> {
        TODO()
    }

    suspend fun hentTaskMedId(
        id: Long,
        navident: String
    ): Ressurs<TaskDto>? {
        TODO()
    }
}
