package utsjekk.utbetaling

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import libs.postgres.concurrency.transaction
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

class UtbetalingDaoTest {

    @Test
    fun `can insert get and delete utbetaling`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId.random()

        transaction {
            val utbet = Utbetaling.dagpenger(1.mar, Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u, Satstype.ENGANGS))
            UtbetalingDao(utbet).insert(uid)
        }
        transaction {
            assertNotNull(UtbetalingDao.findOrNull(uid))
        }
        transaction {
            UtbetalingDao.delete(uid)
        }
        transaction {
            assertNull(UtbetalingDao.findOrNull(uid))
        }
    }

    @Test
    fun `can update utbetaling`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId.random()

        transaction {
            val utbet = Utbetaling.dagpenger(1.mar, Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u, Satstype.ENGANGS))
            UtbetalingDao(utbet).insert(uid)
        }
        transaction {
            val utbet = requireNotNull(UtbetalingDao.findOrNull(uid))
            assertEquals(utbet.created_at, utbet.updated_at)
            utbet.update(uid)
        }
        transaction {
            val utbet = requireNotNull(UtbetalingDao.findOrNull(uid))
            assertNotEquals(utbet.created_at, utbet.updated_at)
        }
        transaction {
            UtbetalingDao.delete(uid)
        }
        transaction {
            assertNull(UtbetalingDao.findOrNull(uid))
        }
    }
}

