package utsjekk.task

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertTrue
import libs.task.TaskDao

class TaskDtoTest {
    @Test
    fun `exponentialSec overskrider ikke ett døgn`() {
        val dao = TaskDao(
            UUID.randomUUID(),
            libs.task.Kind.Utbetaling,
            "",
            libs.task.Status.COMPLETE,
            attempt = 20, // 2^20 sec is more than a day
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null
        )

        val retry = dao.exponentialSec()
        val tomorrow = LocalDateTime.now().plusDays(1)

        assertTrue(retry < tomorrow)
    }

    @Test
    fun `exponentialMin overskrider ikke ett døgn`() {
        val dao = TaskDao(
            UUID.randomUUID(),
            libs.task.Kind.Utbetaling,
            "",
            libs.task.Status.COMPLETE,
            attempt = 20, // 2^20 sec is more than a day
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null
        )

        val retry = dao.exponentialMin()
        val tomorrow = LocalDateTime.now().plusDays(1)

        assertTrue(retry < tomorrow)
    }
}
