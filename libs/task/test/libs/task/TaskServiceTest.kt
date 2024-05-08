package libs.task

import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.postgres.map
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class TaskServiceTest : H2() {
    private val navident = "Æ000000"

    @Nested
    inner class finnAntallTaskerSomKreverOppfølging {
        @Test
        fun `filter out wrong status`() = runTest(h2) {
            transaction {
                enTask(Status.UBEHANDLET).insert()
            }

            val ressurs = TaskService.finnAntallTaskerSomKreverOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data)
        }

        @Test
        fun `match with status MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
            }

            val ressurs = TaskService.finnAntallTaskerSomKreverOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(1, ressurs.data)
        }
    }

    @Nested
    inner class finnAntallTaskerMedStatusFeiletOgManuellOppfølging {
        @Test
        fun `filter out when db is empty`() = runTest(h2) {
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data!!.antallFeilet)
            assertEquals(0, ressurs.data!!.antallManuellOppfølging)
        }

        @Test
        fun `antallFeilet is not dependent on antallManuellOppfølging`() = runTest(h2) {
            transaction {
                enTask(Status.FEILET).insert()
            }
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(1, ressurs.data!!.antallFeilet)
            assertEquals(0, ressurs.data!!.antallManuellOppfølging)
        }

        @Test
        fun `antallManuellOppfølging is not dependent on antallFeilet`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
            }
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data!!.antallFeilet)
            assertEquals(1, ressurs.data!!.antallManuellOppfølging)
        }

        @Test
        fun `antallFeilet and antallManuellOppfølging works together`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
                enTask(Status.MANUELL_OPPFØLGING).insert()
                enTask(Status.FEILET).insert()
            }
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(1, ressurs.data!!.antallFeilet)
            assertEquals(2, ressurs.data!!.antallManuellOppfølging)
        }
    }

    @Nested
    inner class hentTasksSomErFerdigNåMenFeiletFør {
        @Test
        fun `no match when db is empty`() = runTest(h2) {
            val tasks = TaskService.hentTasksSomErFerdigNåMenFeiletFør(navident).data!!
            assertEquals(0, tasks.size)
        }

        @Test
        fun `match when task is FERDIG and previously FEILET`() = runTest(h2) {
            transaction {
                val task = enTask(Status.FERDIG).apply { insert() }
                enTaskLog(task, LogType.FEILET).insert()
            }

            val tasks = TaskService.hentTasksSomErFerdigNåMenFeiletFør(navident)
            assertEquals(Ressurs.Status.SUKSESS, tasks.status)
            assertEquals(1, tasks.data!!.size)
        }

        @Test
        fun `match when task is FERDIG and previously MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                val task = enTask(Status.FERDIG).apply { insert() }
                enTaskLog(task, LogType.MANUELL_OPPFØLGING).insert()
            }

            val tasks = TaskService.hentTasksSomErFerdigNåMenFeiletFør(navident)
            assertEquals(Ressurs.Status.SUKSESS, tasks.status)
            assertEquals(1, tasks.data!!.size)
        }
    }

    @Nested
    inner class hentTasks {
        @Test
        fun `filter out when db is empty`() = runTest(h2) {
            val tasks = TaskService.hentTasks(listOf(Status.FERDIG), navident, null)
            assertEquals(Ressurs.Status.SUKSESS, tasks.status)
            assertEquals(0, tasks.data!!.size)
        }

        @Test
        fun `filter out wrong types`() = runTest(h2) {
            transaction {
                enTask(Status.KLAR_TIL_PLUKK, type = "splendid").insert()
            }

            val ressurs = TaskService.hentTasks(listOf(Status.KLAR_TIL_PLUKK), navident, "awesome")
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data!!.size)
        }

        @Test
        fun `filter out wrong statuses but correct types`() = runTest(h2) {
            transaction {
                enTask(Status.FERDIG, type = "splendid").insert()
            }

            val ressurs = TaskService.hentTasks(listOf(Status.KLAR_TIL_PLUKK), navident, "splendid")
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data!!.size)
        }

        @Test
        fun `filter out wrong statuses`() = runTest(h2) {
            transaction {
                enTask(Status.KLAR_TIL_PLUKK).insert()
            }

            val allButOneStatus = listOf(
                Status.AVVIKSHÅNDTERT,
                Status.BEHANDLER,
                Status.FEILET,
                Status.FERDIG,
                Status.MANUELL_OPPFØLGING,
                Status.PLUKKET,
                Status.UBEHANDLET
            )
            val tasks = TaskService.hentTasks(allButOneStatus, navident, null)
            assertEquals(Ressurs.Status.SUKSESS, tasks.status)
            assertEquals(0, tasks.data!!.size)
        }

        @Test
        fun `match with status`() = runTest(h2) {
            transaction {
                enTask(Status.KLAR_TIL_PLUKK).insert()
            }
            val ressurs = TaskService.hentTasks(listOf(Status.KLAR_TIL_PLUKK), navident, null)
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(1, ressurs.data!!.size)
        }

        @Test
        fun `match with statuses`() = runTest(h2) {
            val allStatuses = listOf(
                Status.AVVIKSHÅNDTERT,
                Status.BEHANDLER,
                Status.FEILET,
                Status.FERDIG,
                Status.KLAR_TIL_PLUKK,
                Status.MANUELL_OPPFØLGING,
                Status.PLUKKET,
                Status.UBEHANDLET
            )

            transaction {
                allStatuses.map {
                    enTask(it).insert()
                }
            }

            val ressurs = TaskService.hentTasks(allStatuses, navident, null)
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(allStatuses.size, ressurs.data!!.size)
        }

        @Test
        fun `match with status and type`() = runTest(h2) {
            transaction {
                enTask(Status.KLAR_TIL_PLUKK, type = "splendid").insert()
            }

            val ressurs = TaskService.hentTasks(listOf(Status.KLAR_TIL_PLUKK), navident, "splendid")
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(1, ressurs.data!!.size)
        }
    }
}

private suspend fun count(table: String): Int =
    transaction {
        coroutineContext.connection
            .prepareStatement("SELECT count(*) FROM $table")
            .executeQuery()
            .map { it.getInt(1) }
            .single()
    }


fun enTask(
    status: Status = Status.UBEHANDLET,
    type: String = ""
) = TaskDao(
    payload = UUID.randomUUID().toString(),
    type = type,
    metadata = null,
    avvikstype = null,
    status = status,
)

fun enTaskLog(task: TaskDao, type: LogType = LogType.FEILET) = TaskLogDao(
    task_id = task.id,
    type = type,
)
