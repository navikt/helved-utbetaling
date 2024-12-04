package utsjekk.task

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertTrue
import libs.task.TaskDao
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

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

    @Test
    fun `exponentialMin justert for helligdag venter til neste dag kl 8 hvis nåværende kjøring er på helligdag`() {
        val helg = LocalDate.of(2024,12,1)
        val dao = TaskDao(
            UUID.randomUUID(),
            libs.task.Kind.Utbetaling,
            "",
            libs.task.Status.IN_PROGRESS,
            attempt = 5,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            scheduledFor = helg.atTime(9,0),
            null
        )

        val retry = dao.exponentialMinAccountForWeekendsAndPublicHolidays()
        val nesteDag = helg.plusDays(1).atTime(8,0)

        assertEquals(nesteDag.truncatedTo(ChronoUnit.MINUTES), retry.truncatedTo(ChronoUnit.MINUTES))
    }

    @Test
    fun `exponentialMin justert for helligdag følger exponentialMin på ukedager`() {
        val ukedag = LocalDate.of(2024,12,3)
        val dao = TaskDao(
            UUID.randomUUID(),
            libs.task.Kind.Utbetaling,
            "",
            libs.task.Status.IN_PROGRESS,
            attempt = 5,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            scheduledFor = ukedag.atTime(9,0),
            null
        )

        val retry = dao.exponentialMinAccountForWeekendsAndPublicHolidays()
        val expected = dao.exponentialMin()

        assertEquals(expected.truncatedTo(ChronoUnit.MINUTES), retry.truncatedTo(ChronoUnit.MINUTES))
    }
}
