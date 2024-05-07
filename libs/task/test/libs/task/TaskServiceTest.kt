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
        fun `found 0 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.UBEHANDLET).insert()
            }

            val ressurs = TaskService.finnAntallTaskerSomKreverOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data)
        }

        @Test
        fun `found 1 MANUELL_OPPFØLGING`() = runTest(h2) {
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
        fun `found 0 FEILET and 0 MANUELL_OPPFØLGIN`() = runTest(h2) {
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data!!.antallFeilet)
            assertEquals(0, ressurs.data!!.antallManuellOppfølging)
        }

        @Test
        fun `found 1 FEILET and 0 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.FEILET).insert()
            }
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(1, ressurs.data!!.antallFeilet)
            assertEquals(0, ressurs.data!!.antallManuellOppfølging)
        }

        @Test
        fun `found 0 FEILET and 1 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
            }
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(0, ressurs.data!!.antallFeilet)
            assertEquals(1, ressurs.data!!.antallManuellOppfølging)
        }

        @Test
        fun `found 1 FEILET and 1 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
                enTask(Status.FEILET).insert()
            }
            val ressurs = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            assertEquals(Ressurs.Status.SUKSESS, ressurs.status)
            assertEquals(1, ressurs.data!!.antallFeilet)
            assertEquals(1, ressurs.data!!.antallManuellOppfølging)
        }
    }

    @Nested
    inner class hentTasksSomErFerdigNåMenFeiletFør {
        @Test
        fun `found 0 FERDIG with previous FEILET`() = runTest(h2) {
            val tasks = TaskService.hentTasksSomErFerdigNåMenFeiletFør(navident).data!!
            assertEquals(0, tasks.size)
        }

        @Test
        fun `found 1 FERDIG with previous FEILET`() = runTest(h2) {
            transaction {
                val task = enTask(Status.FERDIG).apply { insert() }
                enTaskLog(task, Loggtype.FEILET).insert()
            }

            val tasks = TaskService.hentTasksSomErFerdigNåMenFeiletFør(navident)
            assertEquals(Ressurs.Status.SUKSESS, tasks.status)
            assertEquals(1, tasks.data!!.size)
        }

        @Test
        fun `found 1 FERDIG with previous MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                val task = enTask(Status.FERDIG).apply { insert() }
                enTaskLog(task, Loggtype.MANUELL_OPPFØLGING).insert()
            }

            val tasks = TaskService.hentTasksSomErFerdigNåMenFeiletFør(navident)
            assertEquals(Ressurs.Status.SUKSESS, tasks.status)
            assertEquals(1, tasks.data!!.size)
        }
    }

    @Nested
    inner class hentTasks {
        @Test
        fun `found 0 FERDIG`() = runTest(h2) {
            val tasks = TaskService.hentTasks(listOf(Status.FERDIG), navident, null)
            assertEquals(Ressurs.Status.SUKSESS, tasks.status)
            assertEquals(0, tasks.data!!.size)
        }

        @Test
        fun `found 0 ALL STATUSES`() = runTest(h2) {
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
            val tasks = TaskService.hentTasks(allStatuses, navident, null).data!!
            assertEquals(0, tasks.size)
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


fun enTask(status: Status = Status.UBEHANDLET) = TaskDao(
    payload = UUID.randomUUID().toString(),
    type = "",
    metadata = null,
    avvikstype = null,
    status = status,
)

fun enTaskLog(task: TaskDao, type: Loggtype = Loggtype.FEILET) = TaskLogDao(
    task_id = task.id,
    type = type,
)
