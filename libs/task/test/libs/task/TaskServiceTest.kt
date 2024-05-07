package libs.task

import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class TaskServiceTest : H2() {

    @Nested
    inner class finnAntallTaskerSomKreverOppfølging {
        @Test
        fun `found 0 MANUELL_OPPFØLGING`() = runTest(h2) {
            assertEquals(0, TaskService.finnAntallTaskerSomKreverOppfølging().data)

            transaction {
                enTask(Status.UBEHANDLET).insert()
            }

            assertEquals(0, TaskService.finnAntallTaskerSomKreverOppfølging().data)
        }

        @Test
        fun `found 1 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
            }

            val long = TaskService.finnAntallTaskerSomKreverOppfølging()
            assertEquals(1, long.data)
        }

    }

    @Nested
    inner class finnAntallTaskerMedStatusFeiletOgManuellOppfølging {

        @Test
        fun `found 0 FEILET and 0 MANUELL_OPPFØLGIN`() = runTest(h2) {
            val antall = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging().data!!
            assertEquals(0, antall.antallFeilet)
            assertEquals(0, antall.antallManuellOppfølging)
        }

        @Test
        fun `found 1 FEILET and 0 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.FEILET).insert()
            }
            val antall = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging().data!!
            assertEquals(1, antall.antallFeilet)
            assertEquals(0, antall.antallManuellOppfølging)
        }

        @Test
        fun `found 0 FEILET and 1 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
            }
            val antall = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging().data!!
            assertEquals(0, antall.antallFeilet)
            assertEquals(1, antall.antallManuellOppfølging)
        }

        @Test
        fun `found 1 FEILET and 1 MANUELL_OPPFØLGING`() = runTest(h2) {
            transaction {
                enTask(Status.MANUELL_OPPFØLGING).insert()
                enTask(Status.FEILET).insert()
            }
            val antall = TaskService.finnAntallTaskerMedStatusFeiletOgManuellOppfølging().data!!
            assertEquals(1, antall.antallFeilet)
            assertEquals(1, antall.antallManuellOppfølging)
        }
    }

    @Nested
    inner class hentTasksSomErFerdigNåMenFeiletFør {
        private val navident = "Æ000000"

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
            val tasks = TaskService.hentTasksSomErFerdigNåMenFeiletFør(navident).data!!
            assertEquals(1, tasks.size)
        }
    }

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
