package utsjekk.task

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertTrue

class TaskDtoTest {
    @Test
    fun `exponentialSec overskrider ikke ett døgn`() {
        val retry = TaskDto.exponentialSec(20)
        val tomorrow = LocalDateTime.now().plusDays(1)

        assertTrue(retry < tomorrow)
    }

    @Test
    fun `exponentialMin overskrider ikke ett døgn`() {
        val retry = TaskDto.exponentialMin(20)
        val tomorrow = LocalDateTime.now().plusDays(1)

        assertTrue(retry < tomorrow)
    }
}