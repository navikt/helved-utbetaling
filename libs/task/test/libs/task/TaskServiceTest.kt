package libs.task

import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TaskServiceTest : H2() {

    @Test
    fun `finn antall tasks som krever oppfølging = ingen`() = runTest(h2) {
        assertEquals(0, TaskService.finnAntallTaskerSomKreverOppfølging().data)

        transaction {
            val dao = defaultTask(Status.UBEHANDLET)
            dao.insert()
        }

        assertEquals(0, TaskService.finnAntallTaskerSomKreverOppfølging().data)
    }

    @Test
    fun `finn antall tasks som krever oppfølging = 1`() = runTest(h2) {
        transaction {
            val dao = defaultTask(Status.MANUELL_OPPFØLGING)
            dao.insert()
        }

        val long = TaskService.finnAntallTaskerSomKreverOppfølging()
        assertEquals(1, long.data)
    }

}

fun defaultTask(status: Status = Status.UBEHANDLET) = TaskDao(
    payload = "",
    type = "",
    metadata = null,
    avvikstype = null,
    status = status
)
